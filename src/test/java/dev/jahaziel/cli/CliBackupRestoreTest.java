/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.cli.CliTestSupport.Output;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliBackupRestoreTest {
  @TempDir private Path jar;

  private String userDir;

  @BeforeEach
  void pinUserDir() {
    userDir = System.getProperty("user.dir");
    System.setProperty("user.dir", jar.toString());
  }

  @AfterEach
  void restoreUserDir() {
    System.setProperty("user.dir", userDir);
  }

  @Test
  void backupWithSecrets() throws Exception {
    CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
    Files.createDirectories(jar.resolve("data"));
    Files.writeString(jar.resolve("data").resolve("note.txt"), "hi", StandardCharsets.UTF_8);
    Output output = CliTestSupport.dispatch("backup");
    assertEquals(0, output.code());
    assertTrue(output.out().contains("jahaziel: backup: "));
    assertTrue(!output.out().contains("[no secrets]"));
    Path archive = findBackup();
    Process list =
        new ProcessBuilder("tar", "-tzf", archive.toString()).redirectErrorStream(true).start();
    String entries = new String(list.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, list.waitFor());
    assertTrue(entries.contains(".env"));
    assertTrue(entries.contains("data/"));
  }

  @Test
  void backupWithoutEnvFile() {
    Output output = CliTestSupport.dispatch("backup");
    assertEquals(0, output.code());
    assertTrue(output.out().contains("jahaziel: backup: "));
  }

  @Test
  void backupWithoutSecrets() throws Exception {
    CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
    Output output = CliTestSupport.dispatch("backup", "--no-secrets");
    assertEquals(0, output.code());
    assertTrue(output.out().contains("[no secrets]"));
    findBackup();
  }

  @Test
  void backupTarFailure() throws Exception {
    DateTimeFormatter stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    LocalDateTime now = LocalDateTime.now();
    for (int i = -5; i < 40; i++) {
      Files.createDirectory(
          jar.resolve("jahaziel-backup-" + stamp.format(now.plusSeconds(i)) + ".tar.gz"));
    }
    Output output = CliTestSupport.dispatch("backup");
    assertEquals(1, output.code());
    assertTrue(output.err().contains("jahaziel: tar failed (is tar on PATH?)"));
  }

  @Test
  void backupInterruptedCoversExecCatch() throws Exception {
    Path data = jar.resolve("data");
    Files.createDirectories(data);
    byte[] big = new byte[150 * 1024 * 1024];
    new Random(1).nextBytes(big);
    Files.write(data.resolve("big.bin"), big);
    AtomicInteger code = new AtomicInteger(-1);
    Thread worker = new Thread(() -> code.set(CliTestSupport.dispatch("backup").code()));
    worker.setDaemon(true);
    worker.start();
    Path dest = awaitBackupGrowth(30_000);
    worker.interrupt();
    worker.join(60_000);
    assertTrue(!worker.isAlive());
    assertEquals(1, code.get());
    awaitSettled(dest, 60_000);
    CliTestSupport.deleteBestEffort(dest);
  }

  @Test
  void restoreNeedsArg() {
    Output output = CliTestSupport.dispatch("restore");
    assertEquals(2, output.code());
    assertTrue(output.err().contains("restore needs a file"));
  }

  @Test
  void restoreMissingFile() {
    Output output = CliTestSupport.dispatch("restore", "absent.tar.gz");
    assertEquals(1, output.code());
    assertTrue(output.err().contains("jahaziel: no such file: "));
  }

  @Test
  void restoreRejectsNonGzip() throws Exception {
    Path empty = jar.resolve("empty.tar.gz");
    Files.createFile(empty);
    assertEquals(1, CliTestSupport.dispatch("restore", empty.toString()).code());
    assertTrue(CliTestSupport.dispatch("restore", empty.toString()).err().contains("not a gzip"));
    Path one = jar.resolve("one.tar.gz");
    Files.write(one, new byte[] {0x1f});
    assertTrue(CliTestSupport.dispatch("restore", one.toString()).err().contains("not a gzip"));
    Path text = jar.resolve("text.tar.gz");
    Files.writeString(text, "plain text, not gzip", StandardCharsets.UTF_8);
    Output output = CliTestSupport.dispatch("restore", text.toString());
    assertEquals(1, output.code());
    assertTrue(output.err().contains("jahaziel: not a gzip archive: "));
  }

  @Test
  void restoreCannotListFakeGzip() throws Exception {
    Path fake = jar.resolve("fake.tar.gz");
    CliTestSupport.writeGzip(fake, "not a tar".getBytes(StandardCharsets.UTF_8));
    Output output = CliTestSupport.dispatch("restore", fake.toString());
    assertEquals(1, output.code());
    assertTrue(output.err().contains("jahaziel: cannot list "));
  }

  @Test
  void restoreRefusesForeignArchive() throws Exception {
    Path src = jar.resolve("src");
    Files.createDirectories(src);
    Files.writeString(src.resolve("other.txt"), "x", StandardCharsets.UTF_8);
    Path archive = jar.resolve("foreign.tar.gz");
    CliTestSupport.runTar(
        List.of("tar", "-czf", archive.toString(), "-C", src.toString(), "other.txt"), jar);
    Output output = CliTestSupport.dispatch("restore", archive.toString());
    assertEquals(1, output.code());
    assertTrue(output.err().contains("neither data/ nor .env"));
  }

  @Test
  void restoreHappyPathFromOwnBackup() throws Exception {
    CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
    Files.createDirectories(jar.resolve("data"));
    Files.writeString(jar.resolve("data").resolve("keep.txt"), "v", StandardCharsets.UTF_8);
    assertEquals(0, CliTestSupport.dispatch("backup").code());
    Path archive = findBackup();
    Path fresh = Files.createDirectory(jar.resolve("fresh"));
    System.setProperty("user.dir", fresh.toString());
    try {
      Output output = CliTestSupport.dispatch("restore", archive.toString());
      assertEquals(0, output.code());
      assertTrue(output.out().contains("jahaziel: restored "));
      assertTrue(Files.isRegularFile(fresh.resolve(".env")));
      assertTrue(Files.isRegularFile(fresh.resolve("data").resolve("keep.txt")));
    } finally {
      System.setProperty("user.dir", jar.toString());
    }
  }

  @Test
  void restoreExtractFailure() throws Exception {
    Path archive = jar.resolve("link.tar.gz");
    CliTestSupport.writeSymlinkTarGz(archive);
    Output output = CliTestSupport.dispatch("restore", archive.toString());
    assertEquals(1, output.code());
    assertTrue(output.err().contains("jahaziel: extraction failed"));
  }

  private static Path findBackup() throws Exception {
    try (var stream = Files.list(Path.of(System.getProperty("user.dir")))) {
      return stream
          .filter(path -> path.getFileName().toString().startsWith("jahaziel-backup-"))
          .filter(path -> path.getFileName().toString().endsWith(".tar.gz"))
          .filter(Files::isRegularFile)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("no backup found"));
    }
  }

  private Path awaitBackupGrowth(long timeoutMs) throws Exception {
    long end = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < end) {
      try (var stream = Files.list(jar)) {
        for (Path path : stream.toList()) {
          String name = path.getFileName().toString();
          if (name.startsWith("jahaziel-backup-")
              && name.endsWith(".tar.gz")
              && Files.isRegularFile(path)
              && Files.size(path) > 5_000_000L) {
            return path;
          }
        }
      }
      Thread.sleep(50);
    }
    throw new IllegalStateException("backup never started writing");
  }

  private static void awaitSettled(Path file, long timeoutMs) throws Exception {
    long end = System.currentTimeMillis() + timeoutMs;
    long previous = -1;
    int stable = 0;
    while (System.currentTimeMillis() < end) {
      long size = Files.exists(file) ? Files.size(file) : -2;
      if (size == previous) {
        if (++stable >= 2) {
          return;
        }
      } else {
        stable = 0;
        previous = size;
      }
      Thread.sleep(250);
    }
  }
}
