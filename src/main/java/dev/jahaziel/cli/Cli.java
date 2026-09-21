/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import dev.jahaziel.platform.Platforms;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * Offline jar verbs. JahazielMain delegates here first: verbs return an exit code, validated legacy
 * runs return {@link #PASS_THROUGH} so the dispatcher keeps booting Spring.
 *
 * <p>Wiring (dist owner, 3 lines at the top of {@code JahazielMain.run}):
 *
 * <pre>
 * String[] normalized = dev.jahaziel.cli.Cli.normalize(args);
 * int cli = dev.jahaziel.cli.Cli.dispatch(normalized, out, err);
 * if (cli != dev.jahaziel.cli.Cli.PASS_THROUGH) return cli;
 * // ... existing parse/launch continues with {@code normalized}
 * </pre>
 */
public final class Cli {
  /** Returned when args are a validated legacy run: the caller must continue booting. */
  public static final int PASS_THROUGH = Integer.MIN_VALUE;

  /** The five basis-auth variables. Login stays off until all are present. */
  public static final List<String> BASIS_VARS =
      List.of(
          "BASIS_AUTH_ISSUER",
          "BASIS_REDIRECT_URI",
          "BASIS_CLIENT_ID",
          "BASIS_CLIENT_SECRET",
          "BASIS_RESOURCE");

  /** Default API port. */
  public static final int DEFAULT_PORT = 11451;

  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final List<String> VERBS =
      List.of("init", "doctor", "upgrade", "backup", "restore", "logs", "support-bundle");

  private Cli() {}

  /**
   * Developer entry point. The executable jar uses JahazielMain, which delegates here.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    System.exit(dispatch(normalize(args), System.out, System.err));
  }

  /**
   * Passes arguments through unchanged. Flag-only runs ({@code --demo}, {@code --port}, {@code
   * --data-dir}, ...) fall to {@link #dispatch}, which validates them and returns {@link
   * #PASS_THROUGH} so the dispatcher keeps booting Spring.
   *
   * @param args the raw arguments
   * @return the normalized arguments
   */
  public static String[] normalize(String[] args) {
    return Arrays.copyOf(args, args.length);
  }

  /**
   * Handles offline verbs plus run validation and the startup banner.
   *
   * @param args the normalized arguments
   * @param out standard output
   * @param err standard error
   * @return the exit code, or {@link #PASS_THROUGH} for a validated legacy run
   */
  public static int dispatch(String[] args, PrintStream out, PrintStream err) {
    if (args.length == 0) {
      Path jar = jarDir();
      Path env = jar.resolve(".env");
      if (Files.isRegularFile(env)) {
        Map<String, String> merged = merge(parseDotEnv(env), System.getenv());
        printBanner(out, jar, dataDir(args, jar), port(args, merged), backend(merged), false);
        return PASS_THROUGH;
      }
      out.print(usage());
      return 0;
    }
    if (args[0].equals("--help") || args[0].equals("-h")) {
      out.print(usage());
      return 0;
    }
    if (args[0].equals("--version")) {
      out.println("jahaziel " + version());
      return 0;
    }
    try {
      switch (args[0]) {
        case "init" -> {
          return init(jarDir(), out, err);
        }
        case "doctor" -> {
          return doctor(args, out);
        }
        case "upgrade" -> {
          return upgrade(args, out, err);
        }
        case "backup" -> {
          return backup(args, out, err);
        }
        case "restore" -> {
          return restore(args, out, err);
        }
        case "logs" -> {
          return logs(args, out, err);
        }
        case "support-bundle" -> {
          return supportBundle(args, out, err);
        }
        default -> {
          if (args[0].startsWith("-")) {
            return validatedRun(args, out, err);
          }
          err.println("jahaziel: unknown command: " + args[0]);
          err.print(usage());
          return 2;
        }
      }
    } catch (IOException failure) {
      err.println("jahaziel: failed: " + failure.getMessage());
      return 1;
    }
  }

  private static int validatedRun(String[] args, PrintStream out, PrintStream err)
      throws IOException {
    List<String> flags = Arrays.asList(args);
    boolean demo = flags.contains("--demo");
    Path jar = jarDir();
    Map<String, String> fileEnv = parseDotEnv(jar.resolve(".env"));
    Map<String, String> merged = merge(fileEnv, System.getenv());
    printBanner(out, jar, dataDir(args, jar), port(args, merged), backend(merged), demo);
    return PASS_THROUGH;
  }

  /**
   * Creates {@code .env} with BASIS_* placeholders and a random client secret, plus {@code data/}.
   * Refuses to overwrite an existing {@code .env}.
   */
  static int init(Path jar, PrintStream out, PrintStream err) throws IOException {
    Path env = jar.resolve(".env");
    if (Files.exists(env)) {
      err.println("jahaziel: .env already exists: " + env);
      err.println("jahaziel: fix: edit it in place or delete it first");
      return 1;
    }
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String body =
        """
        # Jahaziel single-file install. Fill the placeholders, then run `doctor`.
        BASIS_AUTH_ISSUER=https://idp.example.com
        BASIS_REDIRECT_URI=https://code.example.com/api/oauth/callback
        BASIS_CLIENT_ID=jahaziel
        BASIS_CLIENT_SECRET=%s
        BASIS_RESOURCE=urn:basis:api:jahaziel
        PORT=11451
        JAHAZIEL_LOG_LEVEL=INFO
        JAHAZIEL_DEMO=false
        JAHAZIEL_BACKEND=local
        """
            .formatted(HexFormat.of().formatHex(random));
    Files.createDirectories(jar);
    Files.writeString(env, body, StandardCharsets.UTF_8);
    Files.createDirectories(jar.resolve("data").resolve("tmp"));
    out.println("jahaziel: wrote " + env + " and created data/");
    out.println(
        "jahaziel: next: fill BASIS_* vars, run `doctor`, then boot with no args or --demo");
    return 0;
  }

  private static int doctor(String[] args, PrintStream out) {
    Path jar = jarDir();
    Path data = dataDir(args, jar);
    Map<String, String> fileEnv = parseDotEnv(jar.resolve(".env"));
    return Doctor.print(
        Doctor.run(jar, data, port(args, merge(fileEnv, System.getenv())), fileEnv, jarFile()),
        out);
  }

  private static int upgrade(String[] args, PrintStream out, PrintStream err) throws IOException {
    String target = flag(args, "--to");
    if (Arrays.asList(args).contains("--check") || (target == null && args.length == 1)) {
      out.println("jahaziel " + version() + " (offline: pass `upgrade --to <jar>`)");
      return 0;
    }
    if (target == null) {
      err.println("jahaziel: upgrade needs --to <jar> (or --check)");
      return 2;
    }
    Path from = Path.of(target).toAbsolutePath().normalize();
    if (!Files.isRegularFile(from)) {
      err.println("jahaziel: no such jar: " + from);
      return 1;
    }
    String current = version();
    String next = jarVersion(from);
    if (next.equals(current)) {
      out.println("jahaziel: already on " + current + ", nothing to do");
      return 0;
    }
    Path live = jarFile();
    Path backup = live.resolveSibling("jahaziel.jar.bak-" + STAMP.format(LocalDateTime.now()));
    try {
      Files.copy(live, backup, StandardCopyOption.COPY_ATTRIBUTES);
      Files.copy(from, live, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException locked) {
      err.println("jahaziel: cannot replace " + live + ": " + locked.getMessage());
      err.println("jahaziel: fix: stop the running server first");
      return 1;
    }
    out.println("jahaziel: upgraded " + current + " -> " + next);
    out.println("jahaziel: backup: " + backup + " (" + sha256(from) + ")");
    return 0;
  }

  private static int backup(String[] args, PrintStream out, PrintStream err) throws IOException {
    Path jar = jarDir();
    boolean secrets = !Arrays.asList(args).contains("--no-secrets");
    String name = "jahaziel-backup-" + STAMP.format(LocalDateTime.now()) + ".tar.gz";
    Path dest = jar.resolve(name);
    Files.createDirectories(jar.resolve("data"));
    List<String> sources = new ArrayList<>();
    if (Files.isRegularFile(jar.resolve(".env"))) {
      sources.add(".env");
    }
    sources.add("data");
    List<String> command = new ArrayList<>(List.of("tar", "-czf", dest.toString()));
    if (!secrets) {
      command.add("--exclude=.env");
      command.add("--exclude=data/keys/jwt.key");
    }
    command.add("-C");
    command.add(jar.toString());
    command.addAll(sources);
    int exit = exec(command, jar, out);
    if (exit != 0) {
      Files.deleteIfExists(dest);
      err.println("jahaziel: tar failed (is tar on PATH?)");
      return 1;
    }
    out.println(
        "jahaziel: backup: " + dest + " (" + sha256(dest) + ")" + (secrets ? "" : " [no secrets]"));
    return 0;
  }

  private static int restore(String[] args, PrintStream out, PrintStream err) throws IOException {
    if (args.length < 2) {
      err.println("jahaziel: restore needs a file: `restore <backup.tar.gz>`");
      return 2;
    }
    Path archive = Path.of(args[1]).toAbsolutePath().normalize();
    if (!Files.isRegularFile(archive)) {
      err.println("jahaziel: no such file: " + archive);
      return 1;
    }
    byte[] head = Files.readAllBytes(archive);
    if (head.length < 2 || head[0] != (byte) 0x1f || head[1] != (byte) 0x8b) {
      err.println("jahaziel: not a gzip archive: " + archive);
      return 1;
    }
    Path jar = jarDir();
    ByteArrayOutputStream listed = new ByteArrayOutputStream();
    int exit =
        exec(
            List.of("tar", "-tzf", archive.toString()),
            jar,
            new PrintStream(listed, true, StandardCharsets.UTF_8));
    if (exit != 0) {
      err.println("jahaziel: cannot list " + archive + " (is tar on PATH?)");
      return 1;
    }
    String entries = listed.toString(StandardCharsets.UTF_8);
    if (!entries.contains("data/") && !entries.contains(".env")) {
      err.println("jahaziel: archive holds neither data/ nor .env, refusing");
      return 1;
    }
    exit = exec(List.of("tar", "-xzf", archive.toString(), "-C", jar.toString()), jar, out);
    if (exit != 0) {
      err.println("jahaziel: extraction failed");
      return 1;
    }
    out.println("jahaziel: restored " + archive + " into " + jar);
    return 0;
  }

  private static int logs(String[] args, PrintStream out, PrintStream err) throws IOException {
    List<String> flags = Arrays.asList(args);
    boolean follow = flags.contains("--follow") || flags.contains("-f");
    String level = flag(args, "--level");
    Path log = dataDir(args, jarDir()).resolve("logs").resolve("control-plane.log");
    if (!Files.isRegularFile(log)) {
      err.println("jahaziel: no log file yet: " + log);
      err.println("jahaziel: fix: start the server first");
      return 1;
    }
    int threshold = threshold(level);
    if (level != null && threshold < 0) {
      err.println("jahaziel: unknown level: " + level + " (TRACE, DEBUG, INFO, WARN, ERROR)");
      return 2;
    }
    List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
    int from = Math.max(0, lines.size() - 100);
    for (int i = from; i < lines.size(); i++) {
      emit(lines.get(i), threshold, out);
    }
    long position = Files.size(log);
    while (follow) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return 0;
      }
      long size = Files.size(log);
      if (size < position) {
        position = 0;
      }
      if (size > position) {
        try (var in = Files.newInputStream(log)) {
          in.skip(position);
          String tail = new String(in.readAllBytes(), StandardCharsets.UTF_8);
          for (String line : tail.split("\r?\n")) {
            emit(line, threshold, out);
          }
        }
        position = size;
      }
    }
    return 0;
  }

  private static int supportBundle(String[] args, PrintStream out, PrintStream err)
      throws IOException {
    Path jar = jarDir();
    Path data = dataDir(args, jar);
    Map<String, String> fileEnv = parseDotEnv(jar.resolve(".env"));
    String name = "jahaziel-support-" + STAMP.format(LocalDateTime.now()) + ".zip";
    Path dest = jar.resolve(name);
    ByteArrayOutputStream doctors = new ByteArrayOutputStream();
    Doctor.print(
        Doctor.run(jar, data, port(args, merge(fileEnv, System.getenv())), fileEnv, jarFile()),
        new PrintStream(doctors, true, StandardCharsets.UTF_8));
    try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(dest))) {
      entry(zip, ".env.redacted", redact(fileEnv).getBytes(StandardCharsets.UTF_8));
      entry(zip, "doctor.txt", doctors.toByteArray());
      entry(
          zip,
          "version.txt",
          ("jahaziel "
                  + version()
                  + "\njava "
                  + Runtime.version()
                  + "\n"
                  + Platforms.hostRehVariant()
                  + "\n")
              .getBytes(StandardCharsets.UTF_8));
      Path logs = data.resolve("logs");
      if (Files.isDirectory(logs)) {
        try (var stream = Files.list(logs)) {
          for (Path file : stream.filter(Files::isRegularFile).toList()) {
            if (Files.size(file) < 50_000_000L) {
              entry(zip, "logs/" + file.getFileName(), Files.readAllBytes(file));
            }
          }
        }
      }
    }
    out.println("jahaziel: support bundle: " + dest + " (" + sha256(dest) + ", secrets redacted)");
    return 0;
  }

  /**
   * Prints the startup banner: version, host, java, data, port, backend, ide, status.
   *
   * @param out the output stream
   * @param jar the jar directory
   * @param data the data root
   * @param port the API port
   * @param backend the workspace backend
   * @param demo whether demo mode is on
   */
  public static void printBanner(
      PrintStream out, Path jar, Path data, int port, String backend, boolean demo) {
    String host;
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception unresolved) {
      host = "unknown";
    }
    String ideVersion = System.getenv().getOrDefault("JAHAZIEL_IDE_VERSION", "1.137.0");
    out.println("jahaziel " + version());
    out.println("  version: " + version());
    out.println("  host: " + host);
    out.println(
        "  java: " + Runtime.version() + " (" + System.getProperty("java.vendor", "?") + ")");
    out.println("  data: " + data.toAbsolutePath().normalize());
    out.println("  port: " + port);
    out.println("  backend: " + backend);
    out.println("  ide: " + rehSlug() + " " + ideVersion);
    out.println(
        "  status: starting" + (demo ? " (demo: stub IdP, BASIS_* ignored)" : " (basis-auth)"));
  }

  static String usage() {
    return """
        Usage: java -jar jahaziel.jar COMMAND [options]
                java -jar jahaziel.jar [options]

        No-args with a jar-adjacent .env boots the FULL stack (API + node agent +
        gateway) on 11451. No-args without .env prints this help; run `init` first.

        Commands:
          init                  Write .env (BASIS_* placeholders + random secret), mkdir data/
          doctor                8 pre-boot checks, one line each; exit 0 when all pass
          upgrade --to <jar>    Replace jahaziel.jar offline (backs up the current jar)
          upgrade --check       Print the running version
          backup [--no-secrets] Write jahaziel-backup-<stamp>.tar.gz with .env + data/
          restore <file>        Extract a backup tar.gz into this directory
          logs [--follow] [--level=LEVEL]  Tail data/logs/control-plane.log
          support-bundle        Write jahaziel-support-<stamp>.zip (secrets redacted)

        Run:
          --demo                Stub IdP, seed demo user/workspace, ignore BASIS_*
          --port=N              API port (default: 11451, or PORT from .env)
          --data-dir=PATH       Data root (default: data/ next to the jar)
          --help                Print this help
          --version             Print the version
        """;
  }

  /** Returns the jar version from the manifest, or the development fallback. */
  public static String version() {
    String manifest =
        Cli.class.getPackage() == null ? null : Cli.class.getPackage().getImplementationVersion();
    return manifest == null ? "0.1.0-SNAPSHOT (development)" : manifest;
  }

  static String jarVersion(Path jar) {
    try (JarFile file = new JarFile(jar.toFile())) {
      String value = file.getManifest().getMainAttributes().getValue("Implementation-Version");
      return value == null ? "unknown" : value;
    } catch (IOException unreadable) {
      return "unknown";
    }
  }

  static List<String> missingVars(Map<String, String> merged) {
    return BASIS_VARS.stream().filter(key -> merged.getOrDefault(key, "").isBlank()).toList();
  }

  static Map<String, String> merge(Map<String, String> file, Map<String, String> env) {
    Map<String, String> merged = new LinkedHashMap<>(file);
    merged.putAll(env);
    return Collections.unmodifiableMap(merged);
  }

  static int port(String[] args, Map<String, String> merged) {
    String explicit = flag(args, "--port");
    if (explicit != null) {
      try {
        int value = Integer.parseInt(explicit.strip());
        if (value >= 1 && value <= 65535) {
          return value;
        }
      } catch (NumberFormatException notANumber) {
        // Fall through to PORT below; Arguments.parse rejects the flag later.
      }
    }
    try {
      int value = Integer.parseInt(merged.getOrDefault("PORT", "").strip());
      if (value >= 1 && value <= 65535) {
        return value;
      }
    } catch (NumberFormatException notANumber) {
      // Fall through to the default below.
    }
    return DEFAULT_PORT;
  }

  static String backend(Map<String, String> merged) {
    String value = merged.getOrDefault("JAHAZIEL_BACKEND", "").strip();
    return value.isEmpty() ? "local" : value;
  }

  static Path dataDir(String[] args, Path jar) {
    String explicit = flag(args, "--data-dir");
    if (explicit != null && !explicit.isBlank()) {
      return Path.of(explicit);
    }
    return jar.resolve("data");
  }

  static String flag(String[] args, String name) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith(name + "=")) {
        return args[i].substring(name.length() + 1);
      }
      if (args[i].equals(name) && i + 1 < args.length) {
        return args[i + 1];
      }
    }
    return null;
  }

  static Path jarDir() {
    Path file = jarFile();
    Path parent = file.toAbsolutePath().normalize().getParent();
    return parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
  }

  static Path jarFile() {
    try {
      var source = Cli.class.getProtectionDomain().getCodeSource();
      if (source != null && source.getLocation() != null) {
        Path location = Path.of(source.getLocation().toURI());
        if (location.toString().endsWith(".jar")) {
          return location.toAbsolutePath().normalize();
        }
      }
    } catch (Exception notAJar) {
      // Unpacked (IDE/tests): fall through to user.dir below.
    }
    return Path.of(System.getProperty("user.dir", "."))
        .resolve("jahaziel.jar")
        .toAbsolutePath()
        .normalize();
  }

  /** Minimal .env parser: CRLF/LF, # comments, export prefix, single/double quotes. */
  static Map<String, String> parseDotEnv(Path file) {
    if (!Files.isRegularFile(file)) {
      return Map.of();
    }
    try {
      Map<String, String> values = new LinkedHashMap<>();
      for (String raw : Files.readString(file, StandardCharsets.UTF_8).split("\r?\n")) {
        String line = raw.strip();
        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
          continue;
        }
        if (line.startsWith("export ")) {
          line = line.substring("export ".length()).strip();
        }
        int equals = line.indexOf('=');
        String key = line.substring(0, equals).strip();
        if (key.isEmpty() || key.contains(" ") || key.contains("\t")) {
          continue;
        }
        String value = line.substring(equals + 1).strip();
        int hash = value.indexOf(" #");
        if (hash >= 0 && !value.startsWith("\"") && !value.startsWith("'")) {
          value = value.substring(0, hash);
        }
        values.put(key, unquote(value.strip()));
      }
      return Collections.unmodifiableMap(values);
    } catch (IOException unreadable) {
      return Map.of();
    }
  }

  /** Returns the non-blank lines that are not parseable as KEY=value. */
  static List<String> malformedLines(Path file) {
    try {
      List<String> bad = new ArrayList<>();
      for (String raw : Files.readString(file, StandardCharsets.UTF_8).split("\r?\n")) {
        String line = raw.strip();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        String candidate = line.startsWith("export ") ? line.substring(7).strip() : line;
        int equals = candidate.indexOf('=');
        String key = equals > 0 ? candidate.substring(0, equals).strip() : "";
        if (equals <= 0 || key.isEmpty() || key.contains(" ") || key.contains("\t")) {
          bad.add(line);
        }
      }
      return bad;
    } catch (IOException unreadable) {
      return List.of(unreadable.getMessage());
    }
  }

  static String redact(Map<String, String> fileEnv) {
    StringBuilder redacted = new StringBuilder("# redacted by support-bundle\n");
    fileEnv.forEach(
        (key, value) -> {
          String upper = key.toUpperCase(Locale.ROOT);
          boolean secret =
              upper.contains("SECRET")
                  || upper.contains("PASSWORD")
                  || upper.contains("TOKEN")
                  || upper.startsWith("BASIS_");
          redacted.append(key).append('=').append(secret ? "***" : value).append('\n');
        });
    return redacted.toString();
  }

  /** Returns the REH slug for this host, for example {@code linux-x64}. */
  public static String rehSlug() {
    return Platforms.hostRehVariant();
  }

  /** Returns the lowercase hex sha256 of a file. */
  public static String sha256(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var in = Files.newInputStream(file)) {
        digest.update(in.readAllBytes());
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static int threshold(String level) {
    if (level == null) {
      return -1;
    }
    return switch (level.strip().toUpperCase(Locale.ROOT)) {
      case "TRACE" -> 0;
      case "DEBUG" -> 1;
      case "INFO" -> 2;
      case "WARN", "WARNING" -> 3;
      case "ERROR" -> 4;
      default -> -2;
    };
  }

  private static void emit(String line, int threshold, PrintStream out) {
    if (threshold < 0 || levelOf(line) >= threshold) {
      out.println(line);
    }
  }

  private static int levelOf(String line) {
    String upper = line.toUpperCase(Locale.ROOT);
    if (upper.contains("ERROR")) {
      return 4;
    }
    if (upper.contains("WARN")) {
      return 3;
    }
    if (upper.contains("INFO")) {
      return 2;
    }
    if (upper.contains("DEBUG")) {
      return 1;
    }
    return 0;
  }

  private static void entry(java.util.zip.ZipOutputStream zip, String name, byte[] body)
      throws IOException {
    zip.putNextEntry(new java.util.zip.ZipEntry(name));
    zip.write(body);
    zip.closeEntry();
  }

  private static int exec(List<String> command, Path work, PrintStream out) throws IOException {
    try {
      Process process =
          new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true).start();
      String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exit = process.waitFor();
      if (!text.isBlank()) {
        out.print(text);
      }
      return exit;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted", interrupted);
    }
  }

  private static String unquote(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }
}
