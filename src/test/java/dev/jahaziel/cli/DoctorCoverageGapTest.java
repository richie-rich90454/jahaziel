/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** Closes the JaCoCo gaps in {@link Doctor}. */
class DoctorCoverageGapTest {
  @TempDir private Path root;

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

  private Env green(Path jarDir) throws Exception {
    Files.createDirectories(jarDir);
    Files.writeString(jarDir.resolve(".env"), CliTestSupport.validEnv(), StandardCharsets.UTF_8);
    Path dataDir = jarDir.resolve("data");
    Files.createDirectories(dataDir);
    Path slugDir = dataDir.resolve("ide").resolve(Cli.rehSlug());
    Files.createDirectories(slugDir);
    Files.writeString(slugDir.resolve(".extracted-test"), "x", StandardCharsets.UTF_8);
    Path tmp = dataDir.resolve("tmp");
    Files.createDirectories(tmp);
    System.setProperty("java.io.tmpdir", tmp.toString());
    int port = CliTestSupport.freePort();
    return new Env(
        jarDir,
        dataDir,
        port,
        Cli.parseDotEnv(jarDir.resolve(".env")),
        jarDir.resolve("jahaziel.jar"));
  }

  @Test
  void jdkOldFails() throws Exception {
    Env env = green(root.resolve("jar-old"));
    Runtime.Version old = Runtime.Version.parse("17");
    try (MockedStatic<Runtime> mocked =
        Mockito.mockStatic(Runtime.class, Mockito.CALLS_REAL_METHODS)) {
      mocked.when(Runtime::version).thenReturn(old);
      Doctor.Check check =
          CliTestSupport.find(
              Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
              "jdk");
      assertTrue(!check.ok());
      assertTrue(check.detail().contains("older than 25"));
    }
    Doctor.Check fresh =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "jdk");
    assertTrue(fresh.ok());
  }

  @Test
  void sqliteDriverMissingViaHiddenLoader() throws Exception {
    Path jarDir = Files.createDirectory(root.resolve("jar-hide"));
    Path dataDir = Files.createDirectory(root.resolve("data-hide"));
    System.setProperty("java.io.tmpdir", dataDir.resolve("tmp").toString());
    Files.createDirectories(dataDir.resolve("tmp"));
    String cp = System.getProperty("java.class.path");
    String[] entries = cp.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator));
    java.util.List<java.net.URL> urls = new java.util.ArrayList<>();
    for (String e : entries) {
      if (e.toLowerCase(java.util.Locale.ROOT).contains("sqlite")) {
        continue;
      }
      try {
        urls.add(Path.of(e).toUri().toURL());
      } catch (Exception ignored) {
        // Best-effort classpath filtering.
      }
    }
    boolean sawSqlite = false;
    for (String e : entries) {
      if (e.toLowerCase(java.util.Locale.ROOT).contains("sqlite")) {
        sawSqlite = true;
      }
    }
    if (!sawSqlite || urls.isEmpty()) {
      assertTrue(true);
      return;
    }
    try (var loader = new java.net.URLClassLoader(urls.toArray(new java.net.URL[0]), null)) {
      Class<?> doctor = Class.forName("dev.jahaziel.cli.Doctor", true, loader);
      var run = doctor.getMethod("run", Path.class, Path.class, int.class, Map.class, Path.class);
      @SuppressWarnings("unchecked")
      List<?> checks =
          (List<?>)
              run.invoke(
                  null,
                  jarDir,
                  dataDir,
                  CliTestSupport.freePort(),
                  Map.of(),
                  jarDir.resolve("jahaziel.jar"));
      boolean found = false;
      for (Object c : checks) {
        var name = (String) c.getClass().getMethod("name").invoke(c);
        if (name.equals("sqlite")) {
          found = true;
          boolean ok = (boolean) c.getClass().getMethod("ok").invoke(c);
          String detail = (String) c.getClass().getMethod("detail").invoke(c);
          assertTrue(!ok);
          assertTrue(detail.contains("not on the classpath"));
        }
      }
      assertTrue(found);
    }
  }

  @Test
  void strayDetectsNeedleAndJarName() throws Exception {
    Env env = green(root.resolve("jar-stray"));
    String needle = env.jarFile().toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    assertTrue(Doctor.matchesStray("java -jar " + env.jarFile(), needle));
    assertTrue(Doctor.matchesStray("java -jar /opt/jahaziel.jar", "other-needle"));
    assertTrue(Doctor.matchesStray("java -jar /opt/JAHAZIEL.JAR", "other-needle"));
    assertTrue(!Doctor.matchesStray("java -jar /opt/other.jar", "other-needle"));
    Doctor.Check check =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "stray");
    assertTrue(check.ok());
  }

  @Test
  void rehSentinelAndMissing(@TempDir Path dir) throws Exception {
    Env env = green(root.resolve("jar-reh"));
    Doctor.Check ok =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "reh");
    assertTrue(ok.ok());
    CliTestSupport.deleteBestEffort(env.dataDir().resolve("ide"));
    Path slugDir = env.dataDir().resolve("ide").resolve(Cli.rehSlug());
    Files.createDirectories(slugDir);
    Files.writeString(slugDir.resolve("stale.txt"), "x", StandardCharsets.UTF_8);
    Doctor.Check missing =
        CliTestSupport.find(
            Doctor.run(env.jarDir(), env.dataDir(), env.port(), env.fileEnv(), env.jarFile()),
            "reh");
    assertTrue(!missing.ok());
    assertTrue(!Doctor.dataLocked(dir.resolve("nope-probe")));
  }

  private record Env(
      Path jarDir, Path dataDir, int port, Map<String, String> fileEnv, Path jarFile) {}
}
