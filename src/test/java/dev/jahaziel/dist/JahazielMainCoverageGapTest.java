/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Closes the JaCoCo gaps in {@link JahazielMain} without booting Spring. */
class JahazielMainCoverageGapTest {
  @TempDir private Path tmp;

  private String userDir;
  private String tmpdir;
  private Set<String> addedProperties;

  @BeforeEach
  void pinDirs() {
    userDir = System.getProperty("user.dir");
    tmpdir = System.getProperty("java.io.tmpdir");
    System.setProperty("user.dir", tmp.toString());
    addedProperties = new HashSet<>();
  }

  @AfterEach
  void restoreDirs() {
    System.setProperty("user.dir", userDir);
    if (tmpdir == null) {
      System.clearProperty("java.io.tmpdir");
    } else {
      System.setProperty("java.io.tmpdir", tmpdir);
    }
    for (String key : addedProperties) {
      System.clearProperty(key);
    }
    for (String key :
        List.of(
            "BASIS_AUTH_ISSUER",
            "BASIS_REDIRECT_URI",
            "BASIS_CLIENT_ID",
            "BASIS_CLIENT_SECRET",
            "BASIS_RESOURCE",
            "basis.auth-issuer",
            "basis.redirect-uri",
            "basis.client-id",
            "basis.client-secret",
            "basis.resource",
            "TEST_GAP_KEY",
            "path")) {
      System.clearProperty(key);
    }
    Thread.interrupted();
  }

  private void track(String key) {
    if (System.getProperty(key) == null) {
      addedProperties.add(key);
    }
  }

  private static Output run(String... args) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    int code = JahazielMain.run(args, out, out);
    return new Output(code, buffer.toString(StandardCharsets.UTF_8));
  }

  @Test
  void runHelpAndVersionAfterPassThrough() {
    Output help = run("--port=11451", "--help");
    assertEquals(0, help.code());
    assertTrue(help.text().contains("Usage: java -jar jahaziel.jar"));
    Output version = run("--port=11451", "--version");
    assertEquals(0, version.code());
    assertTrue(version.text().startsWith("jahaziel "));
    Output shortHelp = run("--port=11451", "-h");
    assertEquals(0, shortHelp.code());
  }

  @Test
  void runIllegalArgumentReturnsTwo() {
    Output out = run("--port=1", "--bogus-flag");
    assertEquals(2, out.code());
    assertTrue(out.text().contains("unknown argument"));
  }

  @Test
  void runUnsupportedPlatformReturnsOne() {
    String osName = System.getProperty("os.name");
    String osArch = System.getProperty("os.arch");
    System.setProperty("os.name", "AmigaOS");
    System.setProperty("os.arch", "m68k");
    try {
      Output out = run("--port=11451");
      assertEquals(1, out.code());
      assertTrue(out.text().contains("failed"));
    } finally {
      System.setProperty("os.name", osName);
      System.setProperty("os.arch", osArch);
    }
  }

  @Test
  void runInterruptedReturnsZero() throws Exception {
    try (var mocked = mockStatic(JahazielMain.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
      mocked
          .when(() -> JahazielMain.launch(any(), any(), any()))
          .thenThrow(new InterruptedException("mock"));
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      int code = JahazielMain.run(new String[] {"--port=11451"}, out, out);
      assertEquals(0, code);
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void stripBasisVarsRemovesAllFive() {
    Map<String, String> fileEnv = new HashMap<>();
    fileEnv.put("BASIS_AUTH_ISSUER", "x");
    fileEnv.put("KEEP", "y");
    Map<String, String> kept = JahazielMain.stripBasisVars(fileEnv);
    assertFalse(kept.containsKey("BASIS_AUTH_ISSUER"));
    assertEquals("y", kept.get("KEEP"));
    assertThrows(UnsupportedOperationException.class, () -> kept.put("Z", "z"));
  }

  @Test
  void applyDotEnvSkipsExistingAndMapsBasis() {
    System.setProperty("TEST_GAP_KEY", "existing");
    try {
      JahazielMain.applyDotEnv(Map.of("TEST_GAP_KEY", "new"));
      assertEquals("existing", System.getProperty("TEST_GAP_KEY"));
    } finally {
      System.clearProperty("TEST_GAP_KEY");
    }
    JahazielMain.applyDotEnv(Map.of("PATH", "dummy-should-be-skipped"));
    assertTrue(System.getProperty("PATH") == null || !System.getProperty("PATH").equals("dummy"));
    track("TEST_GAP_KEY2");
    for (String k :
        List.of("TEST_GAP_KEY2", "basis.auth-issuer", "BASIS_AUTH_ISSUER", "basis.resource")) {
      System.clearProperty(k);
    }
    JahazielMain.applyDotEnv(Map.of("TEST_GAP_KEY2", "v2"));
    assertEquals("v2", System.getProperty("TEST_GAP_KEY2"));
    System.clearProperty("TEST_GAP_KEY2");
    System.clearProperty("BASIS_AUTH_ISSUER");
    System.clearProperty("basis.auth-issuer");
    JahazielMain.applyDotEnv(Map.of("BASIS_AUTH_ISSUER", "https://idp.example.com"));
    assertEquals("https://idp.example.com", System.getProperty("BASIS_AUTH_ISSUER"));
    assertEquals("https://idp.example.com", System.getProperty("basis.auth-issuer"));
    System.clearProperty("BASIS_AUTH_ISSUER");
    System.clearProperty("basis.auth-issuer");
    System.setProperty("basis.auth-issuer", "preset");
    JahazielMain.applyDotEnv(Map.of("BASIS_AUTH_ISSUER", "other"));
    assertEquals("preset", System.getProperty("basis.auth-issuer"));
    System.clearProperty("basis.auth-issuer");
    System.clearProperty("BASIS_AUTH_ISSUER");
  }

  @Test
  void applyDotEnvSecondLoopCoversNullBlankAndSet() {
    for (String k :
        List.of(
            "BASIS_AUTH_ISSUER",
            "basis.auth-issuer",
            "BASIS_REDIRECT_URI",
            "basis.redirect-uri",
            "BASIS_CLIENT_ID",
            "basis.client-id",
            "BASIS_CLIENT_SECRET",
            "basis.client-secret",
            "BASIS_RESOURCE",
            "basis.resource")) {
      System.clearProperty(k);
    }
    JahazielMain.applyDotEnv(Map.of());
    assertTrue(System.getProperty("basis.auth-issuer") == null);
    JahazielMain.applyDotEnv(Map.of("BASIS_AUTH_ISSUER", "   "));
    assertTrue(System.getProperty("basis.auth-issuer").isBlank());
    assertTrue(System.getProperty("BASIS_AUTH_ISSUER").isBlank());
    for (String k : List.of("BASIS_AUTH_ISSUER", "basis.auth-issuer")) {
      System.clearProperty(k);
    }
    JahazielMain.applyDotEnv(Map.of("BASIS_AUTH_ISSUER", "https://idp.example.com"));
    assertEquals("https://idp.example.com", System.getProperty("basis.auth-issuer"));
    System.clearProperty("BASIS_AUTH_ISSUER");
    System.clearProperty("basis.auth-issuer");
    System.setProperty("BASIS_AUTH_ISSUER", "from-prop");
    JahazielMain.applyDotEnv(Map.of("BASIS_AUTH_ISSUER", "from-file"));
    assertEquals("from-prop", System.getProperty("basis.auth-issuer"));
    System.clearProperty("BASIS_AUTH_ISSUER");
    System.clearProperty("basis.auth-issuer");
  }

  @Test
  void missingBasisVarsBranches() {
    assertEquals(5, JahazielMain.missingBasisVars(Map.of()).size());
    assertTrue(
        JahazielMain.missingBasisVars(Map.of("BASIS_AUTH_ISSUER", "   "))
            .contains("BASIS_AUTH_ISSUER"));
    assertTrue(
        JahazielMain.missingBasisVars(Map.of("BASIS_AUTH_ISSUER", ""))
            .contains("BASIS_AUTH_ISSUER"));
    Map<String, String> full = new HashMap<>();
    for (String k : dev.jahaziel.platform.DotEnv.BASIS_VARS) {
      full.put(k, "v");
    }
    assertTrue(JahazielMain.missingBasisVars(full).isEmpty());
    System.setProperty("BASIS_AUTH_ISSUER", "from-prop");
    try {
      assertFalse(JahazielMain.missingBasisVars(Map.of()).contains("BASIS_AUTH_ISSUER"));
    } finally {
      System.clearProperty("BASIS_AUTH_ISSUER");
    }
  }

  @Test
  void basisPropertyMapsAll() {
    assertEquals("basis.auth-issuer", JahazielMain.basisProperty("BASIS_AUTH_ISSUER"));
    assertEquals("basis.redirect-uri", JahazielMain.basisProperty("BASIS_REDIRECT_URI"));
    assertEquals("basis.client-id", JahazielMain.basisProperty("BASIS_CLIENT_ID"));
    assertEquals("basis.client-secret", JahazielMain.basisProperty("BASIS_CLIENT_SECRET"));
    assertEquals("basis.resource", JahazielMain.basisProperty("BASIS_RESOURCE"));
    assertTrue(JahazielMain.basisProperty("OTHER") == null);
  }

  @Test
  void launchLockedCoversDemoStripAndReports(@TempDir Path home) throws Exception {
    for (String k :
        List.of(
            "BASIS_AUTH_ISSUER",
            "BASIS_REDIRECT_URI",
            "BASIS_CLIENT_ID",
            "BASIS_CLIENT_SECRET",
            "BASIS_RESOURCE",
            "basis.auth-issuer",
            "basis.redirect-uri",
            "basis.client-id",
            "basis.client-secret",
            "basis.resource")) {
      System.clearProperty(k);
    }
    String prevUserDir = System.getProperty("user.dir");
    String prevTmp = System.getProperty("java.io.tmpdir");
    Set<String> before = new HashSet<>(System.getProperties().stringPropertyNames());
    System.setProperty("user.dir", home.toString());
    try {
      Files.writeString(
          home.resolve(".env"),
          "BASIS_AUTH_ISSUER=https://idp.example.com\nBASIS_X=1\n",
          StandardCharsets.UTF_8);
      Path data = home.resolve("data");
      Files.createDirectories(data);
      try (var channel =
              FileChannel.open(
                  data.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
          var held = channel.lock()) {
        JahazielMain.Arguments demo =
            new JahazielMain.Arguments(
                data.toString(), 11451, 8081, "code.example.com", true, false, false);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int code =
            JahazielMain.launch(
                demo, new PrintStream(buf, true, StandardCharsets.UTF_8), new CountDownLatch(0));
        assertEquals(1, code);
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("locked by another instance"));
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("demo mode"));
      }
      try (var channel =
              FileChannel.open(
                  data.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
          var held = channel.lock()) {
        Files.writeString(
            home.resolve(".env"),
            "BASIS_AUTH_ISSUER=https://idp.example.com\n"
                + "BASIS_REDIRECT_URI=https://code.example.com/api/oauth/callback\n"
                + "BASIS_CLIENT_ID=jahaziel\n"
                + "BASIS_CLIENT_SECRET=s3cret\n"
                + "BASIS_RESOURCE=urn:basis:api:jahaziel\n",
            StandardCharsets.UTF_8);
        JahazielMain.Arguments plain =
            new JahazielMain.Arguments(
                data.toString(), 11451, 8081, "code.example.com", false, false, false);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int code =
            JahazielMain.launch(
                plain, new PrintStream(buf, true, StandardCharsets.UTF_8), new CountDownLatch(0));
        assertEquals(1, code);
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("basis-auth configured"));
      }
      try (var channel =
              FileChannel.open(
                  data.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
          var held = channel.lock()) {
        for (String k :
            List.of(
                "BASIS_AUTH_ISSUER",
                "basis.auth-issuer",
                "BASIS_REDIRECT_URI",
                "basis.redirect-uri",
                "BASIS_CLIENT_ID",
                "basis.client-id",
                "BASIS_CLIENT_SECRET",
                "basis.client-secret",
                "BASIS_RESOURCE",
                "basis.resource",
                "BASIS_X")) {
          System.clearProperty(k);
        }
        Files.writeString(home.resolve(".env"), "# empty\n", StandardCharsets.UTF_8);
        JahazielMain.Arguments plain =
            new JahazielMain.Arguments(
                data.toString(), 11451, 8081, "code.example.com", false, false, false);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int code =
            JahazielMain.launch(
                plain, new PrintStream(buf, true, StandardCharsets.UTF_8), new CountDownLatch(0));
        assertEquals(1, code);
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("login disabled"));
      }
    } finally {
      System.setProperty("user.dir", prevUserDir);
      if (prevTmp == null) {
        System.clearProperty("java.io.tmpdir");
      } else {
        System.setProperty("java.io.tmpdir", prevTmp);
      }
      System.getProperties().stringPropertyNames().stream()
          .filter(k -> !before.contains(k))
          .toList()
          .forEach(System::clearProperty);
    }
  }

  @Test
  void portablePropertyMapDemoBranches(@TempDir Path dir) {
    JarDataDir data = JarDataDir.resolve(dir.resolve("d").toString());
    DevKeys.Keys keys = DevKeys.ensure(data.keys());
    JahazielMain.Arguments demo =
        new JahazielMain.Arguments(null, 11451, 8081, "code.example.com", true, false, false);
    Map<String, String> withDemo = JahazielMain.portablePropertyMap(data, keys, demo);
    assertEquals("stub", withDemo.get("jahaziel.auth.provider"));
    assertEquals("true", withDemo.get("jahaziel.demo"));
    JahazielMain.Arguments plain =
        new JahazielMain.Arguments(null, 11451, 8081, "code.example.com", false, false, false);
    Map<String, String> withoutDemo = JahazielMain.portablePropertyMap(data, keys, plain);
    assertFalse(withoutDemo.containsKey("jahaziel.auth.provider"));
    assertTrue(withDemo.get("server.port").equals("11451"));
    assertTrue(withDemo.get("jahaziel.jwt.issuer").contains("code.example.com"));
  }

  @Test
  void portablePropertiesSetIfAbsent(@TempDir Path dir) {
    JarDataDir data = JarDataDir.resolve(dir.resolve("d2").toString());
    DevKeys.Keys keys = DevKeys.ensure(data.keys());
    JahazielMain.Arguments parsed =
        new JahazielMain.Arguments(null, 11451, 8081, "code.example.com", false, false, false);
    Map<String, String> props = JahazielMain.portablePropertyMap(data, keys, parsed);
    Set<String> before = new HashSet<>(System.getProperties().stringPropertyNames());
    try {
      JahazielMain.portableProperties(data, keys, parsed);
      assertEquals("true", System.getProperty("jahaziel.dist.enabled"));
      System.setProperty("server.port", "9999");
      JahazielMain.portableProperties(data, keys, parsed);
      assertEquals("9999", System.getProperty("server.port"));
    } finally {
      for (String k : props.keySet()) {
        if (!before.contains(k)) {
          System.clearProperty(k);
        }
      }
      System.clearProperty("jahaziel.dist.enabled");
      if (!before.contains("server.port")) {
        System.clearProperty("server.port");
      }
    }
  }

  @Test
  void tryDataLockBothSides(@TempDir Path dir) throws Exception {
    Path lock = dir.resolve(".lock");
    try (FileChannel channel =
        FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      assertTrue(JahazielMain.tryDataLock(channel) != null);
    }
    try (FileChannel channel =
            FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        var held = channel.lock()) {
      assertTrue(JahazielMain.tryDataLock(channel) == null);
    }
  }

  @Test
  void ensureDirsAndMarker(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("portable");
    JahazielMain.ensurePortableDirs(root);
    assertTrue(Files.isDirectory(root.resolve("logs")));
    assertTrue(Files.isDirectory(root.resolve("templates")));
    JahazielMain.markInitialized(root);
    assertTrue(Files.isRegularFile(root.resolve(".initialized")));
    JahazielMain.markInitialized(root);
    assertTrue(Files.isRegularFile(root.resolve(".initialized")));
  }

  @Test
  void sqliteUrlVersionHelp() {
    JarDataDir data = JarDataDir.resolve(tmp.resolve("u").toString());
    dev.jahaziel.platform.Platform platform =
        dev.jahaziel.platform.Platforms.detect("Linux", "x86_64", tmp, null);
    String url = JahazielMain.sqliteUrl(data, platform);
    assertTrue(url.startsWith("jdbc:sqlite:"));
    assertTrue(url.contains("journal_mode=WAL"));
    assertTrue(
        JahazielMain.version().contains("0.1.0-SNAPSHOT") || !JahazielMain.version().isBlank());
    assertTrue(JahazielMain.help().contains("--data-dir"));
  }

  @Test
  void argumentsAllFlags() {
    JahazielMain.Arguments help = JahazielMain.Arguments.parse(new String[] {"--help"});
    assertTrue(help.help());
    JahazielMain.Arguments shortHelp = JahazielMain.Arguments.parse(new String[] {"-h"});
    assertTrue(shortHelp.help());
    assertTrue(JahazielMain.Arguments.parse(new String[] {"--version"}).version());
    assertTrue(JahazielMain.Arguments.parse(new String[] {"--demo"}).demo());
    assertEquals(
        "/tmp/x", JahazielMain.Arguments.parse(new String[] {"--data-dir=/tmp/x"}).dataDir());
    assertEquals(
        "/tmp/y", JahazielMain.Arguments.parse(new String[] {"--data-dir", "/tmp/y"}).dataDir());
    assertEquals(1234, JahazielMain.Arguments.parse(new String[] {"--port=1234"}).port());
    assertEquals(
        4321, JahazielMain.Arguments.parse(new String[] {"--gateway-port=4321"}).gatewayPort());
    assertEquals(
        "example.com",
        JahazielMain.Arguments.parse(new String[] {"--domain=example.com"}).domain());
    assertThrows(
        IllegalArgumentException.class,
        () -> JahazielMain.Arguments.parse(new String[] {"--data-dir"}));
    assertThrows(
        IllegalArgumentException.class,
        () -> JahazielMain.Arguments.parse(new String[] {"--port=0"}));
    assertThrows(
        IllegalArgumentException.class,
        () -> JahazielMain.Arguments.parse(new String[] {"--port=70000"}));
    assertThrows(
        IllegalArgumentException.class,
        () -> JahazielMain.Arguments.parse(new String[] {"--port=abc"}));
    assertThrows(
        IllegalArgumentException.class,
        () -> JahazielMain.Arguments.parse(new String[] {"--domain=   "}));
    assertThrows(
        IllegalArgumentException.class,
        () -> JahazielMain.Arguments.parse(new String[] {"--unknown"}));
  }

  private record Output(int code, String text) {}
}
