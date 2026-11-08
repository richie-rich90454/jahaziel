/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DoctorTest {
  @TempDir private Path root;

  /**
   * Pins SQLite native extraction outside per-test temp dirs. The driver unpacks its DLL once per
   * JVM into a temp dir and keeps it locked on Windows; inside an @TempDir that lock fails JUnit
   * cleanup for whichever test loads it first.
   */
  @BeforeAll
  static void pinSqliteNativeDir() throws Exception {
    if (System.getProperty("org.sqlite.tmpdir") == null) {
      Path dir = Files.createTempDirectory("jahaziel-sqlite-native");
      System.setProperty("org.sqlite.tmpdir", dir.toString());
    }
    Files.createDirectories(Path.of(System.getProperty("org.sqlite.tmpdir")));
  }

  private String tmpdir;

  @BeforeEach
  void saveTmpdir() {
    tmpdir = System.getProperty("java.io.tmpdir");
  }

  @AfterEach
  void restoreTmpdir() {
    if (tmpdir == null) {
      System.clearProperty("java.io.tmpdir");
    } else {
      System.setProperty("java.io.tmpdir", tmpdir);
    }
  }

  private record Env(
      Path jarDir, Path dataDir, int port, Map<String, String> fileEnv, Path jarFile) {}

  private Env green(Path jarDir) throws Exception {
    Files.createDirectories(jarDir);
    Path env = jarDir.resolve(".env");
    Files.writeString(env, CliTestSupport.validEnv(), StandardCharsets.UTF_8);
    Path dataDir = jarDir.resolve("data");
    Files.createDirectories(dataDir);
    Path tmp = dataDir.resolve("tmp");
    Files.createDirectories(tmp);
    System.setProperty("java.io.tmpdir", tmp.toString());
    int port = CliTestSupport.freePort();
    return new Env(jarDir, dataDir, port, Cli.parseDotEnv(env), jarDir.resolve("jahaziel.jar"));
  }

  private static String print(List<Doctor.Check> checks) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Doctor.print(checks, new PrintStream(buffer, true, StandardCharsets.UTF_8));
    return buffer.toString(StandardCharsets.UTF_8);
  }

  @Test
  void allGreen() throws Exception {
    Env env = green(root.resolve("jar"));
    String savedPath = System.getenv("PATH");
    Path bin = Files.createDirectory(root.resolve("allgreen-bin"));
    Path git = Files.createFile(bin.resolve(isWindows() ? "git.exe" : "git"));
    makeExecutable(git);
    dev.jahaziel.platform.TestEnv.set("PATH", bin.toString());
    try {
      List<Doctor.Check> checks =
          Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile());
      assertEquals(11, checks.size());
      for (Doctor.Check check : checks) {
        assertTrue(check.ok(), check.name() + ": " + check.detail());
      }
      String text = print(checks);
      assertTrue(text.contains("doctor: 11/11 checks passed"));
      assertTrue(text.contains("installed managed packs=none"));
      assertTrue(text.contains("selected packs=none"));
      Doctor.Check storage = CliTestSupport.find(checks, "storage");
      assertTrue(
          storage
              .detail()
              .contains("application data total (includes the listed subdirectories)="));
      assertTrue(storage.detail().contains("writable paths: data=" + env.dataDir()));
      assertTrue(storage.detail().contains("workspaces=" + env.dataDir().resolve("workspaces")));
      assertTrue(
          storage.detail().contains("language packs=" + env.dataDir().resolve("language-packs")));
      for (Doctor.Check check : checks) {
        assertTrue(text.contains("doctor: [ok] " + check.name() + " - "));
      }
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      int code = Doctor.print(checks, new PrintStream(buffer, true, StandardCharsets.UTF_8));
      assertEquals(0, code);
    } finally {
      if (savedPath == null) {
        dev.jahaziel.platform.TestEnv.clear("PATH");
      } else {
        dev.jahaziel.platform.TestEnv.set("PATH", savedPath);
      }
    }
  }

  @Test
  void commandAvailableSkipsBlankSegments() throws Exception {
    Path bin = Files.createDirectory(root.resolve("tools-segments-bin"));
    Files.createFile(bin.resolve("tar.exe"));
    String pathValue = java.io.File.pathSeparator + bin;
    assertTrue(Doctor.commandAvailable("tar", pathValue, true));
    assertTrue(!Doctor.commandAvailable("zstd", pathValue, true));
  }

  @Test
  void reportsInstalledAndSelectedManagedLanguagePacks() throws Exception {
    Path cache = Files.createDirectories(root.resolve("language-packs"));
    Path installed = Files.createDirectories(cache.resolve("rust/2026-09-21/aarch64"));
    Files.writeString(installed.resolve(".jahaziel-pack"), "rust\n2026-09-21\nchecksum\n");
    Path selections = Files.createDirectories(cache.resolve("selections"));
    Files.writeString(selections.resolve(UUID.randomUUID() + ".txt"), "rust\nunknown\n");

    String summary = Doctor.managedPackSummary(cache);

    assertTrue(summary.contains("rust@2026-09-21/aarch64"));
    assertTrue(summary.contains("selected packs=rust"));
    assertTrue(!summary.contains("unknown"));
  }

  @Test
  void requiresConfiguredNodeOnlyForSelectedNodeBackedPacks() throws Exception {
    Path selections = Files.createDirectories(root.resolve("data/language-packs/selections"));
    Files.writeString(selections.resolve("python-workspace.txt"), "python\n");
    Path missingNode = root.resolve("missing-node");

    Doctor.Check missing =
        Doctor.languageTools(
            root.resolve("data"),
            Map.of("JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE", missingNode.toString()));

    assertFalse(missing.ok());
    assertTrue(missing.detail().contains("require Node.js"));
    assertTrue(missing.fix().contains("JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE"));

    Files.writeString(selections.resolve("python-workspace.txt"), "java\n");
    Doctor.Check optional =
        Doctor.languageTools(
            root.resolve("data"),
            Map.of("JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE", missingNode.toString()));

    assertTrue(optional.ok(), optional.detail());
    assertTrue(optional.detail().contains("selected language packs=java"));
  }

  @Test
  void reportsSupportedAndUnsupportedServerArchitectures() {
    assertEquals("x64", Doctor.serverArchitecture("amd64"));
    assertEquals("x64", Doctor.serverArchitecture("x86_64"));
    assertEquals("arm64", Doctor.serverArchitecture("aarch64"));
    assertEquals("unsupported (riscv64)", Doctor.serverArchitecture("riscv64"));
  }

  @Test
  void commandAvailableChecksExecutablesOffWindows() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeFalse(isWindows());
    Path bin = Files.createDirectory(root.resolve("tools-posix-bin"));
    Path tar = Files.createFile(bin.resolve("tar"));
    makeExecutable(tar);
    assertTrue(Doctor.commandAvailable("tar", bin.toString(), false));
    assertTrue(!Doctor.commandAvailable("zstd", bin.toString(), false));
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
  }

  private static void makeExecutable(Path file) throws Exception {
    if (!isWindows()) {
      Files.setPosixFilePermissions(
          file,
          EnumSet.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE));
    }
  }

  @Test
  void printMixedShowsFix() {
    List<Doctor.Check> checks =
        List.of(
            new Doctor.Check("a", true, "fine", ""),
            new Doctor.Check("b", false, "broken", "fix b now"));
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int code = Doctor.print(checks, new PrintStream(buffer, true, StandardCharsets.UTF_8));
    assertEquals(1, code);
    String text = buffer.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("doctor: [ok] a - fine"));
    assertTrue(text.contains("doctor: [FAIL] b - broken"));
    assertTrue(text.contains("doctor:   fix: fix b now"));
    assertTrue(text.contains("doctor: 1/2 checks passed"));
  }

  @Test
  void jarDirNotWritable() throws Exception {
    Env env = green(root.resolve("jar"));
    Path probe = env.jarDir().resolve(".doctor-write-probe");
    Files.createDirectories(probe);
    Files.writeString(probe.resolve("child"), "x", StandardCharsets.UTF_8);
    try {
      Doctor.Check check =
          CliTestSupport.find(
              Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
              "jarDir");
      assertTrue(!check.ok());
      assertTrue(check.detail().contains("cannot write to "));
      assertTrue(check.fix().contains("writable directory"));
    } finally {
      CliTestSupport.deleteBestEffort(probe);
    }
  }

  @Test
  void envMissing() throws Exception {
    Path jarDir = Files.createDirectory(root.resolve("jar"));
    Path dataDir = Files.createDirectory(root.resolve("data"));
    System.setProperty("java.io.tmpdir", dataDir.resolve("tmp").toString());
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(
                jarDir,
                dataDir,
                CliTestSupport.freePort(),
                Map.of(),
                jarDir.resolve("jahaziel.jar")),
            "env");
    assertTrue(!check.ok());
    assertTrue(check.detail().contains(".env is missing"));
    assertTrue(check.fix().contains("init"));
  }

  @Test
  void envMalformed() throws Exception {
    Env env = green(root.resolve("jar"));
    Files.writeString(
        env.jarDir().resolve(".env"),
        CliTestSupport.validEnv() + "NOT VALID LINE\n",
        StandardCharsets.UTF_8);
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "env");
    assertTrue(!check.ok());
    assertTrue(check.detail().contains("1 malformed line(s): NOT VALID LINE"));
    assertTrue(check.fix().contains("KEY=value"));
  }

  @Test
  void portOccupiedByUnknownProcess() throws Exception {
    Env env = green(root.resolve("jar"));
    try (ServerSocket held = new ServerSocket(0)) {
      int port = held.getLocalPort();
      Doctor.Check check =
          CliTestSupport.find(
              Doctor.run(env.jarDir(), env.dataDir(), port, env.fileEnv(), env.jarFile()), "port");
      assertTrue(!check.ok());
      assertTrue(check.detail().contains(port + " is in use (holder is an unknown process)"));
      assertTrue(check.fix().contains("--port=N"));
    }
  }

  @Test
  void portHolderLooksLikeJahaziel() throws Exception {
    Env env = green(root.resolve("jar"));
    HttpServer server = serve(200, null);
    try {
      Doctor.Check check = portCheck(env, server.getAddress().getPort());
      assertTrue(!check.ok());
      assertTrue(check.detail().contains("holder looks like another jahaziel instance"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void portHolderHttpErrorWithServer() throws Exception {
    Env env = green(root.resolve("jar"));
    HttpServer server = serve(500, "TestServer/1.0");
    try {
      Doctor.Check check = portCheck(env, server.getAddress().getPort());
      assertTrue(!check.ok());
      assertTrue(check.detail().contains("holder answered HTTP 500 (TestServer/1.0)"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void portHolderHttpErrorWithoutServer() throws Exception {
    Env env = green(root.resolve("jar"));
    HttpServer server = serve(500, null);
    try {
      Doctor.Check check = portCheck(env, server.getAddress().getPort());
      assertTrue(!check.ok());
      assertTrue(check.detail().contains("holder answered HTTP 500)"));
      assertTrue(!check.detail().contains("TestServer"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void sqliteCannotOpenDirectoryAsDb() throws Exception {
    Env env = green(root.resolve("jar"));
    Files.createDirectories(env.dataDir().resolve("jahaziel.db"));
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "sqlite");
    assertTrue(!check.ok());
    assertTrue(check.detail().contains("cannot open "));
    assertTrue(check.fix().contains("--data-dir"));
  }

  @Test
  void storageFailureShowsCodeAndCorrelationIdWithoutRawExceptionDetails() throws Exception {
    Env env = green(root.resolve("jar"));
    Path dataFile = Files.createFile(root.resolve("data-file"));

    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), dataFile, env.port(), env.fileEnv(), env.jarFile()),
            "storage");

    assertFalse(check.ok());
    assertTrue(check.detail().contains("code=STORAGE_INSPECTION_FAILED"));
    assertTrue(check.detail().matches(".*correlationId=[0-9a-f-]{36}\\).*"));
    assertFalse(check.detail().contains(dataFile.toString()));
  }

  @Test
  void tmpdirFailsWhenDataDirIsUnusable() throws Exception {
    Env env = green(root.resolve("jar"));
    Path broken = Files.createTempFile(root, "data", ".txt");
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), broken, env.port(), env.fileEnv(), env.jarFile()), "tmpdir");
    assertTrue(!check.ok());
    assertTrue(check.detail().contains("cannot create "));
    assertTrue(check.fix().contains(broken.toString()));
  }

  @Test
  void tmpdirFailsWhenNotWritable() throws Exception {
    Env env = green(root.resolve("jar"));
    Path tmp = env.dataDir().resolve("tmp");
    try (var files =
        org.mockito.Mockito.mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.isWritable(tmp)).thenReturn(false);
      Doctor.Check check =
          CliTestSupport.find(
              Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
              "tmpdir");
      assertTrue(!check.ok());
      assertTrue(check.detail().contains("is not writable"));
      assertTrue(check.fix().contains(tmp.toString()));
    }
  }

  @Test
  void strayFailsWhileDataLockHeld() throws Exception {
    Env env = green(root.resolve("jar"));
    Path lock = env.dataDir().resolve(".lock");
    try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        var held = channel.lock()) {
      Doctor.Check check =
          CliTestSupport.find(
              Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
              "stray");
      assertTrue(!check.ok());
      assertTrue(check.detail().contains(".lock"));
      assertTrue(check.fix().contains("kill <pid>"));
    }
  }

  @Test
  void strayPassesWhenDataLockFree() throws Exception {
    Env env = green(root.resolve("jar"));
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "stray");
    assertTrue(check.ok());
  }

  @Test
  void strayFailsWhileDataLockHeldByAnotherProcess() throws Exception {
    Env env = green(root.resolve("jar-cross"));
    Path lock = env.dataDir().resolve(".lock");
    Path javaBin = Path.of(System.getProperty("java.home"), "bin", "java.exe");
    String javaCmd = Files.isRegularFile(javaBin) ? javaBin.toString() : "java";
    Process child =
        new ProcessBuilder(
                javaCmd,
                "-cp",
                System.getProperty("java.class.path"),
                DataLockHolder.class.getName(),
                lock.toString())
            .redirectErrorStream(true)
            .start();
    try {
      long end = System.currentTimeMillis() + 15_000;
      boolean seen = false;
      while (System.currentTimeMillis() < end) {
        if (Files.isRegularFile(lock) && Doctor.dataLocked(env.dataDir())) {
          seen = true;
          break;
        }
        Thread.sleep(100);
      }
      assertTrue(seen, "cross-process lock never appeared");
      Doctor.Check check =
          CliTestSupport.find(
              Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
              "stray");
      assertTrue(!check.ok());
      assertTrue(check.detail().contains(".lock"));
    } finally {
      child.destroyForcibly();
      child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
    }
  }

  @Test
  void dataLockedReturnsFalseWhenRootUnusable() throws Exception {
    Path file = root.resolve("not-a-dir");
    Files.writeString(file, "x", StandardCharsets.UTF_8);
    assertTrue(!Doctor.dataLocked(file));
  }

  private Doctor.Check portCheck(Env env, int port) {
    return CliTestSupport.find(
        Doctor.run(env.jarDir(), env.dataDir(), port, env.fileEnv(), env.jarFile()), "port");
  }

  private static HttpServer serve(int status, String server) throws Exception {
    HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    http.createContext(
        "/actuator/health",
        exchange -> {
          if (server != null) {
            exchange.getResponseHeaders().set("Server", server);
          }
          byte[] body = "body".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    http.start();
    return http;
  }
}
