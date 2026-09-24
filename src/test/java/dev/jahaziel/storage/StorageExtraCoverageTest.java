/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.storage.internal.CacheWarmJob;
import dev.jahaziel.storage.internal.ImageMaintenanceJob;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageExtraCoverageTest {
  @TempDir private Path tmp;

  @Test
  void cacheWarmCreatesLayoutAndGuards() throws Exception {
    Path root = tmp.resolve("store");
    new CacheWarmJob(root.toString()).run(JobContext.now());
    assertTrue(Files.isDirectory(root.resolve("caches/maven")));
    assertTrue(Files.isDirectory(root.resolve("caches/npm-ro")));
    assertTrue(Files.isDirectory(root.resolve("caches/cargo-registry")));
    assertTrue(Files.isDirectory(root.resolve("caches/go-modules")));
    assertTrue(Files.isDirectory(root.resolve("caches/pip-wheels")));
    assertEquals(java.time.Duration.ofHours(24 * 7), new CacheWarmJob(root.toString()).period());
    assertEquals(JobScope.NODE, new CacheWarmJob(root.toString()).scope());
    assertThrows(IllegalArgumentException.class, () -> new CacheWarmJob(null));
    assertThrows(IllegalArgumentException.class, () -> new CacheWarmJob("  "));
  }

  @Test
  void cacheWarmFailsWhenFileBlocksDir() throws Exception {
    Path root = tmp.resolve("blocked");
    Files.createDirectories(root);
    Files.writeString(root.resolve("caches"), "file", StandardCharsets.UTF_8);
    assertThrows(
        RuntimeException.class, () -> new CacheWarmJob(root.toString()).run(JobContext.now()));
  }

  @Test
  void imageMaintenanceOkMismatchUnreadable() throws Exception {
    Path dir = tmp.resolve("img");
    Files.createDirectories(dir);
    byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
    Files.write(dir.resolve("base.ext4.zst"), payload);
    String hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
    Files.writeString(dir.resolve("base.ext4.zst.sha256"), hex + "  base.ext4.zst\n");
    new ImageMaintenanceJob(dir, "base").run(JobContext.now());
    assertEquals(java.time.Duration.ofHours(24 * 7), new ImageMaintenanceJob(dir, "x").period());
    assertEquals(JobScope.NODE, new ImageMaintenanceJob(dir, "x").scope());
    Files.writeString(dir.resolve("base.ext4.zst.sha256"), "00" + hex.substring(2));
    assertThrows(
        IllegalStateException.class,
        () -> new ImageMaintenanceJob(dir, "base").run(JobContext.now()));
    assertThrows(
        IllegalStateException.class,
        () -> new ImageMaintenanceJob(dir, "missing").run(JobContext.now()));
    assertThrows(NullPointerException.class, () -> new ImageMaintenanceJob(null, "x"));
    assertThrows(IllegalArgumentException.class, () -> new ImageMaintenanceJob(dir, null));
    assertThrows(IllegalArgumentException.class, () -> new ImageMaintenanceJob(dir, " "));
  }

  @Test
  void imageMaintenanceRefusesWithoutChecksum() throws Exception {
    Path dir = tmp.resolve("nocheck");
    Files.createDirectories(dir);
    Files.write(dir.resolve("b.ext4.zst"), new byte[] {1});
    assertThrows(
        IllegalStateException.class, () -> new ImageMaintenanceJob(dir, "b").run(JobContext.now()));
  }
}
