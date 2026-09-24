/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist.supervisor;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Supervises the platform processes without systemd.
 *
 * <p>This replaces the four host units and the guest units with equivalent behaviour: dependency
 * ordering from {@code After}/{@code Wants}, environment files, per service accounts, restart
 * policy with {@code RestartSec}, {@code LimitNOFILE}, {@code NoNewPrivileges}, start/stop hooks
 * and interval timers. Output is written to one log file per service.
 *
 * <p>Privilege and sandbox directives are applied with util-linux primitives ({@code setpriv} for
 * the account, {@code no_new_privs} and rlimits; {@code unshare} plus bind mounts for the
 * filesystem protections), so nothing here depends on systemd being installed.
 */
public final class ServiceSupervisor {

  private static final String SETPRIV = "setpriv";
  private static final String UNSHARE = "unshare";
  private static final long DEFAULT_KILL_TIMEOUT_SECONDS = 20L;

  private final List<Service> services = new ArrayList<>();
  private final Path logDir;
  private final PrintStream out;
  private final CountDownLatch stopped = new CountDownLatch(1);
  private volatile boolean shuttingDown;

  private ServiceSupervisor(List<ServiceUnit> units, Path logDir, PrintStream out) {
    this.logDir = Objects.requireNonNull(logDir, "logDir");
    this.out = Objects.requireNonNull(out, "out");
    for (ServiceUnit unit : requireOrdered(units)) {
      services.add(new Service(unit));
    }
  }

  /**
   * Starts every unit in {@code servicesDir} and blocks until the supervisor is asked to stop.
   *
   * @param servicesDir directory holding {@code .service} files
   * @param logDir directory for one log file per service
   * @param out console for lifecycle messages
   * @return process exit code
   */
  public static int supervise(Path servicesDir, Path logDir, PrintStream out)
      throws InterruptedException {
    List<ServiceUnit> units = load(servicesDir);
    if (units.isEmpty()) {
      out.println("jahaziel supervisor: no .service files in " + servicesDir);
      return 1;
    }
    ServiceSupervisor supervisor = new ServiceSupervisor(units, logDir, out);
    return supervisor.run();
  }

  /**
   * Lists the units that {@code servicesDir} would start, in start order.
   *
   * @param servicesDir directory holding {@code .service} files
   * @param out console to print to
   * @return process exit code
   */
  public static int status(Path servicesDir, PrintStream out) {
    List<ServiceUnit> units = load(servicesDir);
    out.println("UNIT                          RESTART     ACCOUNT     TIMER     COMMAND");
    for (ServiceUnit unit : requireOrdered(units)) {
      String account = unit.user() == null ? "-" : unit.user();
      String timer = unit.isTimer() ? unit.timerSeconds() + "s" : "-";
      out.printf(
          "%-29s %-11s %-11s %-9s %s%n",
          unit.name(),
          unit.restart().name().toLowerCase(java.util.Locale.ROOT),
          account,
          timer,
          String.join(" ", unit.execStart()));
    }
    return 0;
  }

  static List<ServiceUnit> load(Path servicesDir) {
    if (!Files.isDirectory(servicesDir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(servicesDir)) {
      List<Path> all = files.sorted().toList();
      // A unit's schedule lives in its companion .timer file, so fold the intervals in
      // before anything starts: jahaziel-sync-push.service is periodic, not oneshot.
      Map<String, Long> intervals = new HashMap<>();
      for (Path path : all) {
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".timer")) {
          intervals.put(
              fileName.substring(0, fileName.length() - ".timer".length()),
              ServiceUnit.timerIntervalSeconds(path));
        }
      }
      return all.stream()
          .filter(path -> path.getFileName().toString().endsWith(".service"))
          .map(ServiceUnit::parse)
          .map(unit -> unit.withTimerSeconds(intervals.getOrDefault(unit.name(), 0L)))
          .toList();
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot list " + servicesDir, failure);
    }
  }

  /**
   * Orders units so every {@code After=} dependency we also supervise starts first. Dependencies on
   * units outside the set (network, postgresql) are satisfied by the host and ignored.
   */
  static List<ServiceUnit> requireOrdered(List<ServiceUnit> units) {
    Map<String, ServiceUnit> byName = new LinkedHashMap<>();
    for (ServiceUnit unit : units) {
      byName.put(unit.name(), unit);
    }
    List<ServiceUnit> ordered = new ArrayList<>();
    Set<String> placed = new LinkedHashSet<>();
    Set<String> visiting = new LinkedHashSet<>();
    List<ServiceUnit> sorted = new ArrayList<>(units);
    sorted.sort((first, second) -> first.name().compareTo(second.name()));
    for (ServiceUnit unit : sorted) {
      visit(unit, byName, visiting, placed, ordered);
    }
    return ordered;
  }

  private static void visit(
      ServiceUnit unit,
      Map<String, ServiceUnit> byName,
      Set<String> visiting,
      Set<String> placed,
      List<ServiceUnit> ordered) {
    if (placed.contains(unit.name())) {
      return;
    }
    if (!visiting.add(unit.name())) {
      throw new IllegalStateException("dependency cycle at " + unit.name());
    }
    for (String dependency : unit.after()) {
      ServiceUnit other = byName.get(stripSuffix(dependency));
      if (other != null && !other.name().equals(unit.name())) {
        visit(other, byName, visiting, placed, ordered);
      }
    }
    visiting.remove(unit.name());
    placed.add(unit.name());
    ordered.add(unit);
  }

  private static String stripSuffix(String dependency) {
    int dot = dependency.indexOf('.');
    return dot > 0 ? dependency.substring(0, dot) : dependency;
  }

  // Package-visible (not private) so tests can drive and stub the blocking loop.
  int run() throws InterruptedException {
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "jahaziel-supervisor-stop"));
    try {
      Files.createDirectories(logDir);
    } catch (IOException failure) {
      throw new UncheckedIOException("cannot create " + logDir, failure);
    }
    for (Service service : services) {
      service.start();
    }
    out.println("jahaziel supervisor: " + services.size() + " services up, logs in " + logDir);
    stopped.await();
    return 0;
  }

  /** Stops every service, giving each a grace period before it is killed. */
  void shutdown() {
    if (shuttingDown) {
      return;
    }
    shuttingDown = true;
    List<Service> reversed = new ArrayList<>(services);
    java.util.Collections.reverse(reversed);
    for (Service service : reversed) {
      service.stop();
    }
    stopped.countDown();
  }

  private final class Service implements Runnable {
    private final ServiceUnit unit;
    private final Path logFile;
    private volatile Process process;
    private volatile boolean wanted;
    private volatile Thread worker;
    private volatile int restarts;
    private volatile String state = "stopped";

    private Service(ServiceUnit unit) {
      this.unit = unit;
      this.logFile = logDir.resolve(unit.name() + ".log");
    }

    private void start() {
      wanted = true;
      worker = new Thread(this, "jahaziel-" + unit.name());
      worker.setDaemon(true);
      worker.start();
      try {
        worker.join(TimeUnit.SECONDS.toMillis(2));
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }

    @Override
    public void run() {
      if (unit.isTimer()) {
        runTimer();
        return;
      }
      while (wanted && !shuttingDown) {
        runHooks(unit.execStartPre());
        state = "running";
        int exit = launchOnce();
        state = "exited(" + exit + ")";
        runHooks(unit.execStop());
        if (!wanted || shuttingDown || !shouldRestart(exit)) {
          return;
        }
        restarts++;
        log("restarting in " + unit.restartSec() + "s (exit " + exit + ")");
        sleepSeconds(unit.restartSec());
      }
    }

    private void runTimer() {
      while (wanted && !shuttingDown) {
        state = "running";
        launchOnce();
        state = "waiting";
        sleepSeconds(unit.timerSeconds());
      }
    }

    private int launchOnce() {
      List<String> command = command();
      log("start: " + String.join(" ", command));
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectErrorStream(true);
      builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
      environment().forEach(builder.environment()::put);
      if (unit.workingDirectory() != null) {
        builder.directory(unit.workingDirectory().toFile());
      }
      try {
        Process started = builder.start();
        process = started;
        return started.waitFor();
      } catch (IOException failure) {
        log("cannot start: " + failure.getMessage());
        return 127;
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return 130;
      } finally {
        process = null;
      }
    }

    private boolean shouldRestart(int exit) {
      return switch (unit.restart()) {
        case ALWAYS -> true;
        case ON_FAILURE -> exit != 0;
        case NO -> false;
      };
    }

    private void stop() {
      wanted = false;
      Process running = process;
      if (running != null && running.isAlive()) {
        running.destroy();
        try {
          if (!running.waitFor(DEFAULT_KILL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            running.destroyForcibly();
          }
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          running.destroyForcibly();
        }
      }
      // The worker is deliberately not interrupted: after the process exits it still has to run the
      // ExecStop hook (the workspace sync push). Its sleep polls shuttingDown, so it exits
      // promptly.
      state = "stopped";
    }

    private void runHooks(List<String> hook) {
      if (hook.isEmpty()) {
        return;
      }
      List<String> command = new ArrayList<>(hookPrefix());
      command.addAll(hook);
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectErrorStream(true);
      builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
      environment().forEach(builder.environment()::put);
      try {
        builder.start().waitFor();
      } catch (IOException failure) {
        log("hook failed: " + String.join(" ", hook) + ": " + failure.getMessage());
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }

    private List<String> command() {
      List<String> command = new ArrayList<>(hookPrefix());
      command.addAll(unit.execStart());
      return command;
    }

    private List<String> hookPrefix() {
      return sandboxPrefix(unit);
    }

    private Map<String, String> environment() {
      Map<String, String> environment = new LinkedHashMap<>();
      if (unit.environmentFile() != null && Files.isReadable(unit.environmentFile())) {
        try {
          for (String line : Files.readAllLines(unit.environmentFile(), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
              continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals > 0) {
              environment.put(trimmed.substring(0, equals), trimmed.substring(equals + 1));
            }
          }
        } catch (IOException failure) {
          log("cannot read " + unit.environmentFile() + ": " + failure.getMessage());
        }
      }
      return environment;
    }

    private void log(String message) {
      String line = Instant.now() + " " + unit.name() + ": " + message + System.lineSeparator();
      try {
        Files.writeString(
            logFile,
            line,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      } catch (IOException ignored) {
        // The console copy below is the fallback when the log file is not writable.
      }
      out.print(line);
    }

    /**
     * Waits without holding up shutdown: the loop notices {@code shuttingDown} instead of sleeping
     * through the whole backoff, so a stop during a restart delay exits immediately.
     */
    private void sleepSeconds(long seconds) {
      long deadline = System.nanoTime() + Math.min(seconds, 300L) * 1_000_000_000L;
      while (wanted && !shuttingDown && System.nanoTime() < deadline) {
        try {
          Thread.sleep(200L);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }

    @Override
    public String toString() {
      return unit.name() + "=" + state + " restarts=" + restarts;
    }
  }

  /**
   * Builds the privilege dropping and sandbox prefix for a unit.
   *
   * <p>{@code setpriv} covers the account, {@code no_new_privs} and the fd limit. The filesystem
   * protections ({@code ProtectSystem=strict}, {@code ProtectHome}, {@code PrivateTmp}) need mount
   * namespaces, which {@code unshare -m} provides; the read-write exceptions are re-bound inside
   * it.
   */
  static List<String> sandboxPrefix(ServiceUnit unit) {
    List<String> prefix = new ArrayList<>();
    if (needsSandbox(unit)) {
      prefix.add(UNSHARE);
      prefix.add("--mount");
      prefix.add("--");
    }
    prefix.add(SETPRIV);
    if (unit.noNewPrivileges()) {
      prefix.add("--no-new-privs");
    }
    if (unit.user() != null) {
      prefix.add("--reuid=" + unit.user());
      prefix.add("--regid=" + (unit.group() == null ? unit.user() : unit.group()));
      prefix.add("--clear-groups");
    }
    if (unit.limitNofile() > 0) {
      prefix.add("--rlimit-nofile=" + unit.limitNofile());
    }
    if (unit.protectSystem() || unit.privateTmp() || unit.protectHome()) {
      // sh -c '<mounts>; exec "$@"' sh <command...>: $0 is the placeholder "sh", so "$@" carries
      // the
      // service command through to exec.
      prefix.add("--");
      prefix.add("/bin/sh");
      prefix.add("-c");
      prefix.add(sandboxScript(unit));
      prefix.add("sh");
    }
    return prefix;
  }

  private static boolean needsSandbox(ServiceUnit unit) {
    return unit.protectSystem() || unit.privateTmp() || unit.protectHome();
  }

  /** Bind mounts that approximate the systemd filesystem protections. */
  static String sandboxScript(ServiceUnit unit) {
    StringBuilder script = new StringBuilder();
    if (unit.protectSystem()) {
      script.append("mount -o remount,ro / && ");
    }
    if (unit.privateTmp()) {
      script.append("mount -t tmpfs tmpfs /tmp && ");
    }
    if (unit.protectHome()) {
      script.append(
          "for d in /home /root /run/user; do [ -d \"$d\" ] && mount -t tmpfs tmpfs \"$d\"; done && ");
    }
    for (Path writable : unit.readWritePaths()) {
      script.append("mount -o remount,rw ").append(posix(writable)).append(" 2>/dev/null; ");
    }
    script.append("exec \"$@\"");
    return script.toString();
  }

  /**
   * Renders a path for a POSIX shell. {@link Path#toString()} uses the host separator, so on
   * Windows it would emit backslashes into a script the Linux host runs.
   */
  static String posix(Path path) {
    return path.toString().replace('\\', '/');
  }
}
