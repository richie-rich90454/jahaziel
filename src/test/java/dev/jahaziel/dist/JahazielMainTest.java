/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link JahazielMain} argument handling. No servers start here. */
class JahazielMainTest {
  @TempDir private Path emptyDir;

  private String userDir;

  @BeforeEach
  void pinUserDir() {
    userDir = System.getProperty("user.dir");
    System.setProperty("user.dir", emptyDir.toString());
  }

  @AfterEach
  void restoreUserDir() {
    System.setProperty("user.dir", userDir);
  }

  @Test
  void bareJarIsHelpAlias() {
    Output output = run();
    assertEquals(0, output.code());
    assertTrue(output.text().contains("Usage: java -jar jahaziel.jar"));
  }

  @Test
  void helpAndVersion() {
    assertEquals(0, run("--help").code());
    assertTrue(run("--help").text().contains("Usage: java -jar jahaziel.jar"));
    assertTrue(!run("--help").text().contains("--run=all"));
    assertTrue(run("--version").text().startsWith("jahaziel "));
  }

  @Test
  void rejectsUnknownArgs() {
    assertEquals(2, run("--bogus").code());
    assertEquals(2, run("--run=all").code());
    assertEquals(2, run("--run").code());
    assertEquals(2, run("--services-dir=/tmp").code());
    assertEquals(2, run("--status").code());
    assertEquals(2, run("--port=abc").code());
  }

  @Test
  void portFlagWithoutEnvPrintsHelp() {
    Output output = run("--port=9000");
    assertEquals(0, output.code());
    assertTrue(output.text().contains("Usage: java -jar jahaziel.jar"));
  }

  @Test
  void argumentDefaults() {
    JahazielMain.Arguments parsed = JahazielMain.Arguments.parse(new String[0]);
    assertEquals(11451, parsed.port());
    assertEquals(8081, parsed.gatewayPort());
    assertEquals("localhost", parsed.domain());
    assertNull(parsed.dataDir());
    assertFalse(parsed.demo());
  }

  @Test
  void argumentEnvFallback() {
    Map<String, String> env =
        Map.of("PORT", "9001", "JAHAZIEL_GATEWAY_PORT", "9002", "JAHAZIEL_DOMAIN", "lan.test");
    JahazielMain.Arguments parsed = JahazielMain.Arguments.parse(new String[0], env);
    assertEquals(9001, parsed.port());
    assertEquals(9002, parsed.gatewayPort());
    assertEquals("lan.test", parsed.domain());
  }

  @Test
  void argumentFlagsBeatEnv() {
    Map<String, String> env =
        Map.of("PORT", "9001", "JAHAZIEL_GATEWAY_PORT", "9002", "JAHAZIEL_DOMAIN", "lan.test");
    JahazielMain.Arguments parsed =
        JahazielMain.Arguments.parse(
            new String[] {"--port=9003", "--gateway-port=9004", "--domain=flag.test"}, env);
    assertEquals(9003, parsed.port());
    assertEquals(9004, parsed.gatewayPort());
    assertEquals("flag.test", parsed.domain());
  }

  @Test
  void argumentInvalidEnvFallsBackToDefaults() {
    Map<String, String> env =
        Map.of("PORT", "bogus", "JAHAZIEL_GATEWAY_PORT", "0", "JAHAZIEL_DOMAIN", "  ");
    JahazielMain.Arguments parsed = JahazielMain.Arguments.parse(new String[0], env);
    assertEquals(11451, parsed.port());
    assertEquals(8081, parsed.gatewayPort());
    assertEquals("localhost", parsed.domain());
    Map<String, String> huge = Map.of("PORT", "99999");
    assertEquals(11451, JahazielMain.Arguments.parse(new String[0], huge).port());
  }

  @Test
  void unsupportedPlatformFails() {
    String osName = System.getProperty("os.name");
    String osArch = System.getProperty("os.arch");
    System.setProperty("os.name", "AmigaOS");
    System.setProperty("os.arch", "m68k");
    try {
      Output output = run("--port=9000");
      assertEquals(1, output.code());
      assertTrue(output.text().contains("unsupported platform"));
    } finally {
      System.setProperty("os.name", osName);
      System.setProperty("os.arch", osArch);
    }
  }

  @Test
  void launchBootsAndClosesOnOpenLatch() throws Exception {
    Path home = Files.createTempDirectory("launch-test");
    String previousUserDir = System.getProperty("user.dir");
    String previousTmpdir = System.getProperty("java.io.tmpdir");
    Set<String> before = new HashSet<>(System.getProperties().stringPropertyNames());
    System.setProperty("user.dir", home.toString());
    try {
      Files.writeString(home.resolve(".env"), "# test\n", StandardCharsets.UTF_8);
      JahazielMain.Arguments parsed =
          new JahazielMain.Arguments(
              home.resolve("data").toString(),
              freePort(),
              freePort(),
              "code.example.com",
              false,
              false,
              false);
      int code = JahazielMain.launch(parsed, System.out, new CountDownLatch(0));
      assertEquals(0, code);
      assertTrue(Files.isRegularFile(home.resolve("data").resolve("jahaziel.db")));
      assertTrue(Files.isRegularFile(home.resolve("data").resolve(".initialized")));
    } finally {
      System.setProperty("user.dir", previousUserDir);
      if (previousTmpdir == null) {
        System.clearProperty("java.io.tmpdir");
      } else {
        System.setProperty("java.io.tmpdir", previousTmpdir);
      }
      System.getProperties().stringPropertyNames().stream()
          .filter(key -> !before.contains(key))
          .toList()
          .forEach(System::clearProperty);
      deleteBestEffort(home);
    }
  }

  @Test
  void launchRefusesLockedDataDir() throws Exception {
    Path home = Files.createTempDirectory("launch-locked-test");
    String previousUserDir = System.getProperty("user.dir");
    String previousTmpdir = System.getProperty("java.io.tmpdir");
    Set<String> before = new HashSet<>(System.getProperties().stringPropertyNames());
    System.setProperty("user.dir", home.toString());
    try {
      Files.writeString(home.resolve(".env"), "# test\n", StandardCharsets.UTF_8);
      Path data = home.resolve("data");
      Files.createDirectories(data);
      try (var channel =
              FileChannel.open(
                  data.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
          var held = channel.lock()) {
        JahazielMain.Arguments parsed =
            new JahazielMain.Arguments(
                data.toString(), freePort(), freePort(), "code.example.com", false, false, false);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int code =
            JahazielMain.launch(
                parsed,
                new PrintStream(buffer, true, StandardCharsets.UTF_8),
                new CountDownLatch(0));
        assertEquals(1, code);
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("locked by another instance"));
      }
    } finally {
      System.setProperty("user.dir", previousUserDir);
      if (previousTmpdir == null) {
        System.clearProperty("java.io.tmpdir");
      } else {
        System.setProperty("java.io.tmpdir", previousTmpdir);
      }
      System.getProperties().stringPropertyNames().stream()
          .filter(key -> !before.contains(key))
          .toList()
          .forEach(System::clearProperty);
      deleteBestEffort(home);
    }
  }

  private static int freePort() throws java.io.IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static void deleteBestEffort(Path root) {
    try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
      walk.sorted(java.util.Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                  // Windows may still hold the boot log; the OS reclaims temp.
                }
              });
    } catch (Exception ignored) {
      // Best-effort test cleanup.
    }
  }

  private static Output run(String... args) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    int code = JahazielMain.run(args, out, out);
    return new Output(code, buffer.toString(StandardCharsets.UTF_8));
  }

  private record Output(int code, String text) {}
}
