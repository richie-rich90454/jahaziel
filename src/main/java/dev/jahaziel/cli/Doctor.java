/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import dev.jahaziel.language.api.LanguageRuntimeRequirements;
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Pre-boot environment checks. Eleven checks, one line each, exit 0 only when all pass. Pure JDK so
 * the jar can diagnose itself before Spring starts.
 */
public final class Doctor {
  private Doctor() {}

  /** A single check result. */
  public record Check(String name, boolean ok, String detail, String fix) {}

  /**
   * Runs all eleven checks.
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
    checks.add(hostAndBackend(fileEnv));
    checks.add(languageTools(dataDir, fileEnv));
    checks.add(storage(dataDir));
    checks.add(tmpDir(dataDir));
    checks.add(noStray(jarFile, dataDir));
    checks.add(gitCli());
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

  private static Check hostAndBackend(Map<String, String> fileEnv) {
    String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);
    String architecture = serverArchitecture(arch);
    boolean linux = os.contains("linux");
    String backend =
        System.getenv()
            .getOrDefault("JAHAZIEL_BACKEND", fileEnv.getOrDefault("JAHAZIEL_BACKEND", "local"));
    if (!backend.equalsIgnoreCase("local")) {
      return new Check(
          "host",
          false,
          "This installation supports the local workspace backend only.",
          "set JAHAZIEL_BACKEND=local");
    }
    if (linux && architecture.startsWith("unsupported")) {
      return new Check(
          "host",
          false,
          "This Linux host architecture is not supported: " + architecture,
          "use a Linux x64 or arm64 server");
    }
    if (linux
        && !Files.isExecutable(Path.of("/bin/bash"))
        && !Files.isExecutable(Path.of("/bin/sh"))) {
      return new Check(
          "host",
          false,
          "no supported interactive shell is available",
          "provide /bin/bash or /bin/sh");
    }
    if (linux
        && !Files.isExecutable(Path.of("/usr/bin/setsid"))
        && !Files.isExecutable(Path.of("/bin/setsid"))) {
      return new Check(
          "host",
          false,
          "setsid is unavailable; task and language-service process groups cannot be recovered",
          "install util-linux through the Linux distribution package manager");
    }
    String host =
        linux ? "Linux " + architecture : "non-server desktop host " + os + " " + architecture;
    String detail =
        host
            + "; backend="
            + backend.toLowerCase(Locale.ROOT)
            + "; language packs are opt-in under data/language-packs";
    if (!linux) {
      detail += "; production servers target Linux x64 and arm64";
    }
    return new Check("host", true, detail, "");
  }

  static String serverArchitecture(String arch) {
    if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
    if (arch.contains("x86_64") || arch.contains("amd64") || arch.contains("x64")) return "x64";
    return "unsupported (" + arch + ")";
  }

  static Check languageTools(Path dataDir, Map<String, String> fileEnv) {
    String configuredNode =
        System.getenv()
            .getOrDefault(
                "JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE",
                fileEnv.getOrDefault("JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE", "/usr/bin/node"));
    if (configuredNode.isBlank()) {
      return new Check(
          "toolchains",
          false,
          "configured Node.js executable path is blank",
          "set JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE to an existing Node.js executable");
    }
    Path node;
    try {
      node = Path.of(configuredNode).toAbsolutePath().normalize();
    } catch (java.nio.file.InvalidPathException invalid) {
      return new Check(
          "toolchains",
          false,
          "configured Node.js executable path is invalid",
          "set JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE to an existing Node.js executable");
    }

    List<String> selected;
    try {
      selected =
          selectedLanguagePacks(dataDir.toAbsolutePath().normalize().resolve("language-packs"));
    } catch (IOException failure) {
      return new Check(
          "toolchains",
          false,
          "Selected language-pack data cannot be read safely.",
          "repair data/language-packs/selections or fix its permissions");
    }
    boolean nodeRequired = selected.stream().anyMatch(LanguageRuntimeRequirements::usesNodeRuntime);
    boolean nodeAvailable = Files.isRegularFile(node) && Files.isExecutable(node);
    if (nodeRequired && !nodeAvailable) {
      return new Check(
          "toolchains",
          false,
          "selected TypeScript or Python packs require Node.js; " + node + " is unavailable",
          "set JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE to an existing Linux Node.js executable");
    }
    String nodeStatus =
        nodeAvailable
            ? "Node.js available at " + node
            : "Node.js is optional until a TypeScript or Python pack is selected (configured "
                + node
                + ")";
    return new Check(
        "toolchains",
        true,
        "selected language packs="
            + (selected.isEmpty() ? "none" : String.join(",", selected))
            + "; "
            + nodeStatus,
        "");
  }

  private static List<String> selectedLanguagePacks(Path cacheRoot) throws IOException {
    Path selections = cacheRoot.resolve("selections");
    if (!Files.exists(selections, LinkOption.NOFOLLOW_LINKS)) return List.of();
    if (Files.isSymbolicLink(selections)
        || !Files.isDirectory(selections, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("selection path is not a plain directory");
    }
    Set<String> known = Set.of("cpp", "java", "go", "rust", "typescript", "python");
    Set<String> selected = new TreeSet<>();
    try (Stream<Path> files = Files.list(selections)) {
      for (Path file :
          files.filter(path -> path.getFileName().toString().endsWith(".txt")).toList()) {
        if (Files.isSymbolicLink(file)
            || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
            || Files.size(file) > 4096) {
          throw new IOException("selection file is not a bounded plain file");
        }
        for (String id : Files.readAllLines(file, StandardCharsets.UTF_8)) {
          if (known.contains(id.strip())) selected.add(id.strip());
        }
      }
    }
    return List.copyOf(selected);
  }

  private static Check gitCli() {
    boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    if (commandAvailable("git", System.getenv("PATH"), windows)) {
      return new Check("git", true, "Git CLI resolves on PATH", "");
    }
    return new Check(
        "git",
        false,
        "Git CLI is unavailable; source-control panel operations cannot run",
        "install Git through the Linux distribution package manager and restart Jahaziel");
  }

  private static Check storage(Path dataDir) {
    Path root = dataDir.toAbsolutePath().normalize();
    Path workspaces = root.resolve("workspaces");
    Path packs = root.resolve("language-packs");
    try {
      Files.createDirectories(workspaces);
      Files.createDirectories(packs);
      if (!Files.isWritable(root) || !Files.isWritable(workspaces) || !Files.isWritable(packs)) {
        return new Check(
            "storage",
            false,
            "data, workspace, or language-pack directory is not writable",
            "fix ownership and permissions under " + root);
      }
      long appBytes = directoryBytes(root);
      long workspaceBytes = directoryBytes(workspaces);
      long packBytes = directoryBytes(packs);
      String packSummary = managedPackSummary(packs);
      return new Check(
          "storage",
          true,
          "application data total (includes the listed subdirectories)="
              + appBytes
              + " bytes, workspaces="
              + workspaceBytes
              + " bytes, language-pack cache="
              + packBytes
              + " bytes; writable paths: data="
              + root
              + ", workspaces="
              + workspaces
              + ", language packs="
              + packs
              + "; "
              + packSummary,
          "");
    } catch (IOException failure) {
      return new Check(
          "storage",
          false,
          "cannot inspect application data: " + failure.getMessage(),
          "fix permissions on " + root);
    }
  }

  static String managedPackSummary(Path root) throws IOException {
    List<String> installed = new ArrayList<>();
    List<String> selected = new ArrayList<>();
    List<String> packIds = List.of("cpp", "java", "go", "rust", "typescript", "python");
    if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) {
      for (String packId : packIds) {
        Path packRoot = root.resolve(packId);
        if (!Files.isDirectory(packRoot, LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(packRoot)) continue;
        try (Stream<Path> versions = Files.list(packRoot)) {
          for (Path version :
              versions
                  .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                  .filter(path -> !Files.isSymbolicLink(path))
                  .sorted()
                  .toList()) {
            try (Stream<Path> architectures = Files.list(version)) {
              for (Path architecture :
                  architectures
                      .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                      .filter(path -> !Files.isSymbolicLink(path))
                      .sorted()
                      .toList()) {
                Path marker = architecture.resolve(".jahaziel-pack");
                if (Files.isSymbolicLink(marker)
                    || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(marker) > 4096) continue;
                List<String> lines = Files.readAllLines(marker, StandardCharsets.UTF_8);
                if (lines.size() >= 2
                    && lines.getFirst().equals(packId)
                    && lines.get(1).equals(version.getFileName().toString())) {
                  installed.add(packId + "@" + lines.get(1) + "/" + architecture.getFileName());
                }
              }
            }
          }
        }
      }
      Path selections = root.resolve("selections");
      if (Files.isDirectory(selections, LinkOption.NOFOLLOW_LINKS)
          && !Files.isSymbolicLink(selections)) {
        try (Stream<Path> files = Files.list(selections)) {
          for (Path file :
              files
                  .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                  .filter(path -> !Files.isSymbolicLink(path))
                  .filter(path -> path.getFileName().toString().endsWith(".txt"))
                  .toList()) {
            if (Files.size(file) > 4096) continue;
            for (String id : Files.readAllLines(file, StandardCharsets.UTF_8)) {
              if (packIds.contains(id.strip()) && !selected.contains(id.strip())) {
                selected.add(id.strip());
              }
            }
          }
        }
      }
    }
    installed.sort(String::compareTo);
    selected.sort(String::compareTo);
    return "installed managed packs="
        + (installed.isEmpty() ? "none" : String.join(",", installed))
        + "; selected packs="
        + (selected.isEmpty() ? "none" : String.join(",", selected))
        + " (packs download only after explicit selection)";
  }

  private static long directoryBytes(Path root) throws IOException {
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
      return 0;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      return paths
          .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          .mapToLong(
              path -> {
                try {
                  return Files.size(path);
                } catch (IOException ignored) {
                  return 0;
                }
              })
          .sum();
    }
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
