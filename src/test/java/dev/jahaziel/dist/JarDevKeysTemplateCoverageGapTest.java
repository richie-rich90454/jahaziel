/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/** Closes the JaCoCo gaps in {@link JarDataDir}, {@link DevKeys} and {@link TemplateSeeds}. */
class JarDevKeysTemplateCoverageGapTest {
  @Test
  void resolveBlankUsesAdjacent(@TempDir Path dir) {
    String prev = System.getProperty("user.dir");
    System.setProperty("user.dir", dir.toString());
    try {
      JarDataDir blank = JarDataDir.resolve("   ");
      assertEquals(dir.resolve("data").toAbsolutePath().normalize(), blank.root());
      JarDataDir nul = JarDataDir.resolve(null);
      assertEquals(dir.resolve("data").toAbsolutePath().normalize(), nul.root());
      JarDataDir explicit = JarDataDir.resolve(dir.resolve("x").toString());
      assertTrue(Files.isDirectory(explicit.workspaces()));
    } finally {
      System.setProperty("user.dir", prev);
    }
  }

  @Test
  void ensureIoFailure(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("blocker");
    Files.writeString(file, "x", StandardCharsets.UTF_8);
    assertThrows(
        java.io.UncheckedIOException.class,
        () -> JarDataDir.resolve(file.resolve("sub").toString()));
  }

  @Test
  void adjacentBaseFromJar(@TempDir Path dir) throws Exception {
    Path fakeJar = dir.resolve("app.jar");
    Files.createFile(fakeJar);
    File mockFile = Mockito.mock(File.class);
    Mockito.when(mockFile.getName()).thenReturn("app.jar");
    Mockito.when(mockFile.getAbsoluteFile()).thenReturn(mockFile);
    Mockito.when(mockFile.getParentFile()).thenReturn(dir.toFile());
    Mockito.when(mockFile.getPath()).thenReturn(dir.toString());
    try (var ignored =
        Mockito.mockConstruction(
            org.springframework.boot.system.ApplicationHome.class,
            (mock, ctx) -> Mockito.when(mock.getSource()).thenReturn(mockFile))) {
      assertEquals(dir.toString(), JarDataDir.adjacentBase());
    }
    File plainFile = Mockito.mock(File.class);
    Mockito.when(plainFile.getName()).thenReturn("classes");
    try (var ignored =
        Mockito.mockConstruction(
            org.springframework.boot.system.ApplicationHome.class,
            (mock, ctx) -> Mockito.when(mock.getSource()).thenReturn(plainFile))) {
      String prev = System.getProperty("user.dir");
      try {
        System.setProperty("user.dir", dir.toString());
        assertEquals(dir.toString(), JarDataDir.adjacentBase());
      } finally {
        System.setProperty("user.dir", prev);
      }
    }
  }

  @Test
  void devKeysSingleFileRegenerates(@TempDir Path dir) throws Exception {
    Path keys = dir.resolve("keys");
    Files.createDirectories(keys);
    Files.writeString(keys.resolve("jwt.key"), "partial", StandardCharsets.UTF_8);
    DevKeys.Keys ensured = DevKeys.ensure(keys);
    assertTrue(Files.isRegularFile(ensured.privateKey()));
    assertTrue(Files.isRegularFile(ensured.publicKey()));
    DevKeys.Keys again = DevKeys.ensure(keys);
    assertEquals(ensured, again);
  }

  @Test
  void devKeysLockStuckThrows(@TempDir Path dir) throws Exception {
    Path keys = dir.resolve("stuck");
    Files.createDirectories(keys);
    Files.createFile(keys.resolve(".lock"));
    long start = System.currentTimeMillis();
    assertThrows(IllegalStateException.class, () -> DevKeys.ensure(keys));
    assertTrue(System.currentTimeMillis() - start >= 5_000);
    Files.deleteIfExists(keys.resolve(".lock"));
  }

  @Test
  void devKeysLockWaitSeesExistingKeys(@TempDir Path dir) throws Exception {
    Path seed = dir.resolve("seed");
    DevKeys.Keys real = DevKeys.ensure(seed);
    Path keys = dir.resolve("racing");
    Files.createDirectories(keys);
    Files.copy(real.privateKey(), keys.resolve("jwt.key"));
    Files.copy(real.publicKey(), keys.resolve("jwt.pub"));
    Files.createFile(keys.resolve(".lock"));
    DevKeys.Keys got = DevKeys.ensure(keys);
    assertTrue(Files.isRegularFile(got.privateKey()));
    Files.deleteIfExists(keys.resolve(".lock"));
  }

  @Test
  void devKeysInterruptedLockWait(@TempDir Path dir) throws Exception {
    Path keys = dir.resolve("interrupted");
    Files.createDirectories(keys);
    Files.createFile(keys.resolve(".lock"));
    Thread worker =
        new Thread(
            () -> {
              try {
                DevKeys.ensure(keys);
              } catch (IllegalStateException expected) {
                // Stuck path after interrupt.
              }
            });
    worker.setDaemon(true);
    worker.start();
    Thread.sleep(300);
    worker.interrupt();
    worker.join(15_000);
    assertTrue(!worker.isAlive());
    Files.deleteIfExists(keys.resolve(".lock"));
  }

  @Test
  void devKeysSecondCheckAfterLock(@TempDir Path dir) throws Exception {
    Path keys = dir.resolve("afterlock");
    Files.createDirectories(keys);
    DevKeys.Keys first = DevKeys.ensure(keys);
    Files.delete(keys.resolve("jwt.pub"));
    Files.writeString(
        keys.resolve("jwt.key"), Files.readString(first.privateKey()), StandardCharsets.UTF_8);
    assertThrows(
        IllegalStateException.class,
        () -> {
          Path onlyPrivate = dir.resolve("onlyprivate");
          Files.createDirectories(onlyPrivate);
          Files.copy(first.privateKey(), onlyPrivate.resolve("jwt.key"));
          Files.createFile(onlyPrivate.resolve(".lock"));
          try {
            DevKeys.ensure(onlyPrivate);
          } finally {
            Files.deleteIfExists(onlyPrivate.resolve(".lock"));
          }
        });
  }

  @Test
  void devKeysBadDirThrows(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("filekeys");
    Files.writeString(file, "x", StandardCharsets.UTF_8);
    assertThrows(IllegalStateException.class, () -> DevKeys.ensure(file));
    assertThrows(NullPointerException.class, () -> DevKeys.ensure(null));
    assertThrows(NullPointerException.class, () -> new DevKeys.Keys(null, dir.resolve("p")));
  }

  @Test
  void devKeysWritePemFallbacks(@TempDir Path dir) throws Exception {
    Path keys = dir.resolve("pem");
    DevKeys.Keys got = DevKeys.ensure(keys);
    assertTrue(Files.isRegularFile(got.privateKey()));
    String priv = Files.readString(got.privateKey(), StandardCharsets.UTF_8);
    assertTrue(priv.contains("BEGIN PRIVATE KEY"));
  }

  @Test
  void devKeysSecondCheckAfterLockReturns(@TempDir Path dir) throws Exception {
    Path keys = dir.resolve("secondcheck");
    DevKeys.Keys real = DevKeys.ensure(dir.resolve("seed-src"));
    Files.createDirectories(keys);
    Files.copy(real.privateKey(), keys.resolve("jwt.key"));
    Files.copy(real.publicKey(), keys.resolve("jwt.pub"));
    assertEquals(real.privateKey().getFileName(), keys.resolve("jwt.key").getFileName());
    java.util.concurrent.atomic.AtomicInteger calls =
        new java.util.concurrent.atomic.AtomicInteger();
    Path priv = keys.resolve("jwt.key").toAbsolutePath().normalize();
    Path pub = keys.resolve("jwt.pub").toAbsolutePath().normalize();
    try (var mocked = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      mocked
          .when(() -> Files.isRegularFile(any(Path.class)))
          .thenAnswer(
              inv -> {
                Path p = inv.getArgument(0);
                Path norm = p.toAbsolutePath().normalize();
                if ((norm.equals(priv) || norm.equals(pub)) && calls.getAndIncrement() < 2) {
                  return false;
                }
                return (Boolean) inv.callRealMethod();
              });
      DevKeys.Keys got = DevKeys.ensure(keys);
      assertTrue(Files.isRegularFile(got.privateKey()));
    }
  }

  @Test
  void devKeysPosixSuccessPath(@TempDir Path dir) throws Exception {
    Path keys = dir.resolve("posix");
    try (var mocked = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      mocked
          .when(() -> Files.setPosixFilePermissions(any(Path.class), any(Set.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      DevKeys.Keys got = DevKeys.ensure(keys);
      assertTrue(Files.isRegularFile(got.privateKey()));
    }
  }

  @Test
  void devKeysAtomicMoveFallback(@TempDir Path dir) throws Exception {
    Path keys = dir.resolve("atomic");
    try (var mocked = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      mocked
          .when(
              () ->
                  Files.move(
                      any(Path.class),
                      any(Path.class),
                      any(java.nio.file.CopyOption.class),
                      any(java.nio.file.CopyOption.class)))
          .thenThrow(new java.nio.file.AtomicMoveNotSupportedException("x", "y", "mock"));
      DevKeys.Keys got = DevKeys.ensure(keys);
      assertTrue(Files.isRegularFile(got.privateKey()));
    }
  }

  @Test
  void devKeysWriteFailureCleansStaged(@TempDir Path dir) throws Exception {
    Path keys = dir.resolve("writefail");
    Files.createDirectories(keys);
    try (var mocked = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      mocked
          .when(() -> Files.writeString(any(Path.class), any(CharSequence.class)))
          .thenThrow(new java.io.IOException("mock"));
      assertThrows(IllegalStateException.class, () -> DevKeys.ensure(keys));
    }
    try (var mocked = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      mocked
          .when(() -> Files.createTempFile(any(Path.class), any(String.class), any(String.class)))
          .thenThrow(new java.io.IOException("mock"));
      assertThrows(IllegalStateException.class, () -> DevKeys.ensure(dir.resolve("tmpfail")));
    }
  }

  @Test
  void templateSeedsBothBranches(@TempDir Path dir) throws Exception {
    Path templates = dir.resolve("templates");
    TemplateSeeds.copyIfAbsent(templates);
    for (String name : TemplateSeeds.SEEDS) {
      assertTrue(Files.isRegularFile(templates.resolve(name).resolve("template.json")));
    }
    TemplateSeeds.copyIfAbsent(templates);
    Path custom = templates.resolve("empty").resolve("template.json");
    Files.writeString(custom, "{\"name\":\"mine\"}", StandardCharsets.UTF_8);
    TemplateSeeds.copyIfAbsent(templates);
    assertEquals("{\"name\":\"mine\"}", Files.readString(custom, StandardCharsets.UTF_8));
  }
}
