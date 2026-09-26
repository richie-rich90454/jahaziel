/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Pre-boot environment checks. Nine checks, one line each, exit 0 only when all pass. Pure JDK so
 * the jar can diagnose itself before Spring starts.
 */
public final class Doctor {
  private Doctor() {}

  /** A single check result. */
  public record Check(String name, boolean ok, String detail, String fix) {}

  /**
   * Runs all nine checks.
   *
   * @param jarDir the directory holding the jar and .env
   * @param dataDir the data root
   * @param port the API port to probe
   * @param fileEnv the parsed .env values
   * @param jarFile the running jar, for the stray-process scan
   * @return the results in stable order
   */
  public static List<Check> run(
      Path jarDir, Path dataDir, int port, Map<String, String> fileEnv, Path jarFile) {
    List<Check> checks = new ArrayList<>();
    checks.add(jdk());
    checks.add(jarDirWritable(jarDir));
    checks.add(dotEnv(jarDir, fileEnv));
    checks.add(portFree(port));
    checks.add(sqlite(dataDir));
    checks.add(reh(dataDir));
    checks.add(tools());
    checks.add(tmpDir(dataDir));
    checks.add(noStray(jarFile, dataDir));
    return List.copyOf(checks);
  }

  /**
   * Prints the results, one line per check plus a summary.
   *
   * @param checks the results
   * @param out the output stream
   * @return 0 when every check passed, else 1
   */
  public static int print(List<Check> checks, PrintStream out) {
    long passed = 0;
    for (Check check : checks) {
      if (check.ok()) {
        passed++;
        out.println("doctor: [ok] " + check.name() + " - " + check.detail());
      } else {
        out.println("doctor: [FAIL] " + check.name() + " - " + check.detail());
        out.println("doctor:   fix: " + check.fix());
      }
    }
    out.println("doctor: " + passed + "/" + checks.size() + " checks passed");
    return passed == checks.size() ? 0 : 1;
  }

  private static Check jdk() {
    int feature = Runtime.version().feature();
    if (feature >= 25) {
      return new Check("jdk", true, "java " + Runtime.version(), "");
    }
    return new Check(
        "jdk",
        false,
        "java " + Runtime.version() + " is older than 25",
        "run with JDK 25 or newer (java -version)");
  }

  private static Check jarDirWritable(Path jarDir) {
    Path probe = jarDir.resolve(".doctor-write-probe");
    try {
      Files.createDirectories(jarDir);
      Files.writeString(probe, "probe", StandardCharsets.UTF_8);
      Files.deleteIfExists(probe);
      return new Check("jarDir", true, jarDir + " is writable", "");
    } catch (IOException failure) {
      return new Check(
          "jarDir",
          false,
          "cannot write to " + jarDir + ": " + failure.getMessage(),
          "run from a writable directory or fix permissions on " + jarDir);
    }
  }

  private static Check dotEnv(Path jarDir, Map<String, String> fileEnv) {
    Path env = jarDir.resolve(".env");
    if (!Files.isRegularFile(env)) {
      return new Check(
          "env", false, ".env is missing in " + jarDir, "run `java -jar jahaziel.jar init`");
    }
    List<String> malformed = Cli.malformedLines(env);
    if (!malformed.isEmpty()) {
      return new Check(
          "env",
          false,
          ".env has " + malformed.size() + " malformed line(s): " + malformed.get(0),
          "fix " + env + " (KEY=value, no spaces around =)");
    }
    return new Check("env", true, ".env present with " + fileEnv.size() + " entries", "");
  }

  private static Check portFree(int port) {
    try (ServerSocket socket = new ServerSocket(port)) {
      socket.setReuseAddress(true);
      return new Check("port", true, port + " is free", "");
    } catch (IOException inUse) {
      return new Check("port", false, port + " is in use (" + holder(port) + ")", holderFix(port));
    }
  }

  private static String holder(int port) {
    try {
      HttpURLConnection probe =
          (HttpURLConnection)
              URI.create("http://127.0.0.1:" + port + "/actuator/health").toURL().openConnection();
      probe.setConnectTimeout(2000);
      probe.setReadTimeout(2000);
      int status = probe.getResponseCode();
      String server = probe.getHeaderField("Server");
      if (status == 200) {
        return "holder looks like another jahaziel instance";
      }
      return "holder answered HTTP " + status + (server == null ? "" : " (" + server + ")");
    } catch (Exception unreachable) {
      return "holder is an unknown process";
    }
  }

  private static String holderFix(int port) {
    return "stop the holder or start with --port=N (and PORT=N in .env)";
  }

  private static Check sqlite(Path dataDir) {
    Path db = dataDir.resolve("jahaziel.db");
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException missing) {
      return new Check(
          "sqlite",
          false,
          "sqlite driver is not on the classpath",
          "run checks via `java -jar jahaziel.jar doctor`");
    }
    try {
      Files.createDirectories(dataDir);
      // ponytail: driver is bundled in the jar; reflective load keeps CLI compilable standalone.
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + db);
          var statement = connection.createStatement()) {
        statement.execute("CREATE TABLE IF NOT EXISTS doctor_probe(id INTEGER PRIMARY KEY)");
        statement.execute("INSERT INTO doctor_probe DEFAULT VALUES");
        statement.execute("DELETE FROM doctor_probe");
      }
      return new Check("sqlite", true, db + " opens and writes", "");
    } catch (Exception failure) {
      return new Check(
          "sqlite",
          false,
          "cannot open " + db + ": " + failure.getMessage(),
          "fix permissions on " + dataDir + " or move --data-dir somewhere writable");
    }
  }

  private static Check reh(Path dataDir) {
    String slug = Cli.rehSlug();
    Path tarball = dataDir.resolve("ide").resolve("jahaziel-code-server-" + slug + ".tar.zst");
    Path checksum = tarball.resolveSibling(tarball.getFileName() + ".sha256");
    if (Files.isRegularFile(tarball) && Files.isRegularFile(checksum)) {
      try {
        String expected =
            Files.readString(checksum, StandardCharsets.UTF_8).strip().split("\\s+")[0];
        String actual = Cli.sha256(tarball);
        if (actual.equalsIgnoreCase(expected)) {
          return new Check("reh", true, tarball.getFileName() + " present, sha256 matches", "");
        }
        return new Check(
            "reh",
            false,
            "sha256 mismatch for " + tarball.getFileName(),
            "re-fetch the REH tarball and its .sha256 (ide/build.sh or fetch the manifest)");
      } catch (IOException failure) {
        return new Check(
            "reh",
            false,
            "cannot verify " + tarball.getFileName() + ": " + failure.getMessage(),
            "re-fetch the REH tarball and its .sha256");
      }
    }
    Path extracted = dataDir.resolve("ide").resolve(slug);
    java.io.File[] children = extracted.toFile().listFiles();
    if (children != null) {
      for (java.io.File child : children) {
        if (child.getName().startsWith(".extracted-")) {
          return new Check("reh", true, slug + " already extracted", "");
        }
      }
    }
    return new Check(
        "reh",
        false,
        "REH tarball missing: " + tarball,
        "build it with ide/build.sh or fetch the manifest for " + slug);
  }

  /**
   * Checks the external tools REH extraction shells out to. Runs on the ambient PATH because that
   * is what the extraction subprocess inherits.
   */
  private static Check tools() {
    return tools(System.getenv("PATH"));
  }

  static Check tools(String pathValue) {
    boolean windows = Platforms.detect(null).os() == Platform.Os.WINDOWS;
    return tools(pathValue, windows);
  }

  static Check tools(String pathValue, boolean windows) {
    List<String> missing = new ArrayList<>();
    if (!commandAvailable("tar", pathValue, windows)) {
      missing.add("tar");
    }
    if (!commandAvailable("zstd", pathValue, windows)) {
      missing.add("zstd");
    }
    if (missing.isEmpty()) {
      return new Check("tools", true, "tar and zstd resolve on PATH", "");
    }
    return new Check(
        "tools",
        false,
        "missing on PATH: " + String.join(", ", missing),
        "install zstd and launch from a shell where tar and zstd resolve"
            + " (ide/build.sh already needs both)");
  }

  static boolean commandAvailable(String command, String pathValue, boolean windows) {
    if (pathValue == null || pathValue.isBlank()) {
      return false;
    }
    for (String dir : pathValue.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
      if (dir.isBlank()) {
        continue;
      }
      if (windows) {
        for (String candidate :
            List.of(command, command + ".exe", command + ".cmd", command + ".bat")) {
          if (Files.isRegularFile(Path.of(dir, candidate))) {
            return true;
          }
        }
      } else if (Files.isExecutable(Path.of(dir, command))) {
        return true;
      }
    }
    return false;
  }

  private static Check tmpDir(Path dataDir) {
    // The launch path forces java.io.tmpdir to <data>/tmp before Spring starts, so diagnose that
    // directory instead of the ambient JVM property, which the server overrides anyway.
    Path root = dataDir.toAbsolutePath().normalize();
    Path tmp = root.resolve("tmp");
    try {
      Files.createDirectories(tmp);
    } catch (IOException failure) {
      return new Check(
          "tmpdir",
          false,
          "cannot create " + tmp + ": " + failure.getMessage(),
          "fix permissions on " + root);
    }
    if (Files.isWritable(tmp)) {
      return new Check("tmpdir", true, tmp + " is writable inside data/", "");
    }
    return new Check("tmpdir", false, tmp + " is not writable", "fix permissions on " + tmp);
  }

  static boolean matchesStray(String commandLine, String needle) {
    String lower = commandLine.toLowerCase(Locale.ROOT);
    return lower.contains(needle) || lower.contains("jahaziel.jar");
  }

  /**
   * Decides whether one process looks like another Jahaziel server. Pure decision split out of
   * {@link #noStray} so unit tests cover it without spawning processes (Windows never exposes
   * command lines, so the live scan alone cannot exercise the match on this OS).
   *
   * @param process the candidate process
   * @param needle the lowercase jar path to look for
   * @param self the current pid, never reported
   * @return the signal text, or empty when the process is unrelated
   */
  static Optional<String> straySignal(ProcessHandle process, String needle, long self) {
    if (process.pid() == self) {
      return Optional.empty();
    }
    String cmd = process.info().commandLine().orElseGet(() -> process.info().command().orElse(""));
    if (cmd.isEmpty() || !matchesStray(cmd, needle)) {
      return Optional.empty();
    }
    return Optional.of("pid " + process.pid());
  }

  private static Check noStray(Path jarFile, Path dataDir) {
    long self = ProcessHandle.current().pid();
    String needle = jarFile.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    List<String> signals = new ArrayList<>();
    ProcessHandle.allProcesses()
        .forEach(process -> straySignal(process, needle, self).ifPresent(signals::add));
    if (dataLocked(dataDir)) {
      signals.add("data lock " + dataDir.resolve(".lock"));
    }
    if (signals.isEmpty()) {
      return new Check("stray", true, "no other java process runs this jar", "");
    }
    return new Check(
        "stray",
        false,
        "another instance is running (" + String.join(", ", signals) + ")",
        "stop them before starting (kill <pid>) or they will fight over data/jahaziel.db");
  }

  /**
   * Probes the data-directory lock without holding it. True when another process holds it (the
   * running server keeps it for its whole lifetime) or this JVM already does.
   *
   * @param dataDir the data root
   * @return true when the lock is held
   */
  static boolean dataLocked(Path dataDir) {
    Path root = dataDir.toAbsolutePath().normalize();
    try {
      Files.createDirectories(root);
      try (var channel =
          FileChannel.open(
              root.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
        return channel.tryLock() == null;
      }
    } catch (OverlappingFileLockException alreadyHeld) {
      return true;
    } catch (IOException unreadable) {
      return false;
    }
  }
}
