/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import java.util.Map;
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
    Path slugDir = dataDir.resolve("ide").resolve(Cli.rehSlug());
    Files.createDirectories(slugDir);
    Files.writeString(slugDir.resolve(".extracted-test"), "x", StandardCharsets.UTF_8);
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
    Files.createFile(bin.resolve("tar.exe"));
    Files.createFile(bin.resolve("zstd.exe"));
    dev.jahaziel.platform.TestEnv.set("PATH", bin.toString());
    try {
      List<Doctor.Check> checks =
          Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile());
      assertEquals(9, checks.size());
      for (Doctor.Check check : checks) {
        assertTrue(check.ok());
      }
      String text = print(checks);
      assertTrue(text.contains("doctor: 9/9 checks passed"));
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
  void toolsCheckPassesWhenBothPresent() throws Exception {
    Path bin = Files.createDirectory(root.resolve("tools-bin"));
    Files.createFile(bin.resolve("tar.exe"));
    Files.createFile(bin.resolve("zstd.exe"));
    Doctor.Check check = Doctor.tools(bin.toString(), true);
    assertTrue(check.ok());
    assertEquals("tools", check.name());
  }

  @Test
  void toolsCheckNamesMissingCommands() throws Exception {
    Path bin = Files.createDirectory(root.resolve("tools-partial-bin"));
    Files.createFile(bin.resolve("tar.exe"));
    Doctor.Check check = Doctor.tools(bin.toString(), true);
    assertTrue(!check.ok());
    assertTrue(check.detail().contains("zstd"));
    assertTrue(check.fix().contains("zstd"));
    Path bin2 = Files.createDirectory(root.resolve("tools-partial-bin2"));
    Files.createFile(bin2.resolve("zstd.exe"));
    Doctor.Check check2 = Doctor.tools(bin2.toString(), true);
    assertTrue(!check2.ok());
    assertTrue(check2.detail().contains("tar"));
  }

  @Test
  void toolsCheckFailsOnBlankPath() {
    assertTrue(!Doctor.tools(null, true).ok());
    assertTrue(!Doctor.tools("   ", true).ok());
  }

  @Test
  void toolsCheckReadsOsFromSystemProperties() throws Exception {
    String osName = System.getProperty("os.name");
    String osArch = System.getProperty("os.arch");
    try {
      System.setProperty("os.name", "Linux");
      System.setProperty("os.arch", "amd64");
      Path bin = Files.createDirectory(root.resolve("tools-linux-bin"));
      Files.createFile(bin.resolve("tar"));
      Files.createFile(bin.resolve("zstd"));
      assertTrue(Doctor.tools(bin.toString()).ok());
    } finally {
      restoreProperty("os.name", osName);
      restoreProperty("os.arch", osArch);
    }
  }

  private static void restoreProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
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
  void commandAvailableChecksExecutablesOffWindows() throws Exception {
    Path bin = Files.createDirectory(root.resolve("tools-posix-bin"));
    Files.createFile(bin.resolve("tar"));
    assertTrue(Doctor.commandAvailable("tar", bin.toString(), false));
    assertTrue(!Doctor.commandAvailable("zstd", bin.toString(), false));
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
  void rehMissingWithoutIdeDir() throws Exception {
    Env env = green(root.resolve("jar"));
    CliTestSupport.deleteBestEffort(env.dataDir().resolve("ide"));
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "reh");
    assertTrue(!check.ok());
    assertTrue(check.detail().contains("REH tarball missing: "));
    assertTrue(check.fix().contains("ide/build.sh"));
  }

  @Test
  void rehMissingWithTarballButNoChecksum() throws Exception {
    Env env = green(root.resolve("jar"));
    CliTestSupport.deleteBestEffort(env.dataDir().resolve("ide"));
    Path tarball = rehTarball(env.dataDir());
    Files.createDirectories(tarball.getParent());
    Files.write(tarball, new byte[] {1, 2, 3});
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "reh");
    assertTrue(!check.ok());
    assertTrue(check.detail().contains("REH tarball missing: "));
  }

  @Test
  void rehMissingWhenExtractedDirHasNoSentinel() throws Exception {
    Env env = green(root.resolve("jar"));
    CliTestSupport.deleteBestEffort(env.dataDir().resolve("ide"));
    Path slugDir = env.dataDir().resolve("ide").resolve(Cli.rehSlug());
    Files.createDirectories(slugDir);
    Files.writeString(slugDir.resolve("stale.txt"), "x", StandardCharsets.UTF_8);
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "reh");
    assertTrue(!check.ok());
    assertTrue(check.detail().contains("REH tarball missing: "));
  }

  @Test
  void rehChecksumMatch() throws Exception {
    Env env = green(root.resolve("jar"));
    CliTestSupport.deleteBestEffort(env.dataDir().resolve("ide"));
    Path tarball = rehTarball(env.dataDir());
    Files.createDirectories(tarball.getParent());
    Files.write(tarball, new byte[] {9, 8, 7});
    Path checksum = tarball.resolveSibling(tarball.getFileName() + ".sha256");
    Files.writeString(
        checksum, Cli.sha256(tarball) + "  " + tarball.getFileName(), StandardCharsets.UTF_8);
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "reh");
    assertTrue(check.ok());
    assertTrue(check.detail().contains("sha256 matches"));
  }

  @Test
  void rehChecksumMismatch() throws Exception {
    Env env = green(root.resolve("jar"));
    CliTestSupport.deleteBestEffort(env.dataDir().resolve("ide"));
    Path tarball = rehTarball(env.dataDir());
    Files.createDirectories(tarball.getParent());
    Files.write(tarball, new byte[] {9, 8, 7});
    Path checksum = tarball.resolveSibling(tarball.getFileName() + ".sha256");
    Files.writeString(checksum, "0".repeat(64), StandardCharsets.UTF_8);
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "reh");
    assertTrue(!check.ok());
    assertTrue(check.detail().contains("sha256 mismatch"));
    assertTrue(check.fix().contains("re-fetch"));
  }

  @Test
  void rehUnreadableChecksum() throws Exception {
    Env env = green(root.resolve("jar"));
    CliTestSupport.deleteBestEffort(env.dataDir().resolve("ide"));
    Path tarball = rehTarball(env.dataDir());
    Files.createDirectories(tarball.getParent());
    Files.write(tarball, new byte[] {9, 8, 7});
    Path checksum = tarball.resolveSibling(tarball.getFileName() + ".sha256");
    Files.write(checksum, new byte[] {(byte) 0xFF, (byte) 0xFE});
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "reh");
    assertTrue(!check.ok());
    assertTrue(check.detail().contains("cannot verify "));
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

  private static Path rehTarball(Path dataDir) {
    return dataDir.resolve("ide").resolve("jahaziel-code-server-" + Cli.rehSlug() + ".tar.zst");
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
