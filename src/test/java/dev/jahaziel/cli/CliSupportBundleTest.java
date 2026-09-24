/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.cli.CliTestSupport.Output;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliSupportBundleTest {
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
  void bundleRedactsAndIncludesDoctorVersionLogs() throws Exception {
    CliTestSupport.writeEnv(
        jar,
        "BASIS_CLIENT_SECRET=s3cret\n"
            + "MY_PASSWORD=hunter2\n"
            + "API_TOKEN=tok\n"
            + "NORMAL=keep\n"
            + "PORT=11451\n");
    Path logs = jar.resolve("data").resolve("logs");
    Files.createDirectories(logs);
    Files.writeString(logs.resolve("app.log"), "hello logs", StandardCharsets.UTF_8);
    Output output = CliTestSupport.dispatch("support-bundle");
    assertEquals(0, output.code());
    assertTrue(output.out().contains("jahaziel: support bundle: "));
    assertTrue(output.out().contains("secrets redacted"));
    Path zip = findBundle();
    String redacted = CliTestSupport.zipEntry(zip, ".env.redacted");
    assertTrue(redacted.contains("BASIS_CLIENT_SECRET=***"));
    assertTrue(redacted.contains("MY_PASSWORD=***"));
    assertTrue(redacted.contains("API_TOKEN=***"));
    assertTrue(redacted.contains("NORMAL=keep"));
    assertTrue(redacted.contains("PORT=11451"));
    assertTrue(CliTestSupport.zipEntry(zip, "doctor.txt").contains("doctor: "));
    String version = CliTestSupport.zipEntry(zip, "version.txt");
    assertTrue(version.contains("jahaziel " + Cli.version()));
    assertTrue(version.contains("java " + Runtime.version()));
    assertTrue(version.contains(Cli.rehSlug()));
    assertEquals("hello logs", CliTestSupport.zipEntry(zip, "logs/app.log"));
  }

  @Test
  void bundleWithoutLogsDir() throws Exception {
    CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
    Output output = CliTestSupport.dispatch("support-bundle");
    assertEquals(0, output.code());
    Path zip = findBundle();
    assertTrue(CliTestSupport.zipEntry(zip, "doctor.txt").contains("doctor: "));
  }

  @Test
  void bundleSkipsHugeFilesAndDirectories() throws Exception {
    CliTestSupport.writeEnv(jar, CliTestSupport.validEnv());
    Path logs = jar.resolve("data").resolve("logs");
    Files.createDirectories(logs);
    Files.writeString(logs.resolve("small.log"), "s", StandardCharsets.UTF_8);
    Files.createDirectories(logs.resolve("subdir"));
    try (RandomAccessFile big = new RandomAccessFile(logs.resolve("big.bin").toFile(), "rw")) {
      big.setLength(51_000_000L);
    }
    assertEquals(0, CliTestSupport.dispatch("support-bundle").code());
    Path zip = findBundle();
    assertTrue(CliTestSupport.zipHas(zip, "logs/small.log"));
    assertTrue(!CliTestSupport.zipHas(zip, "logs/big.bin"));
    assertTrue(!CliTestSupport.zipHas(zip, "logs/subdir"));
  }

  @Test
  void bundleIoFailureMapsToOne() throws Exception {
    Path file = jar.resolve("blocker");
    Files.writeString(file, "x", StandardCharsets.UTF_8);
    System.setProperty("user.dir", file.toString());
    try {
      Output output = CliTestSupport.dispatch("support-bundle");
      assertEquals(1, output.code());
      assertTrue(output.err().contains("jahaziel: failed: "));
    } finally {
      System.setProperty("user.dir", jar.toString());
    }
  }

  private Path findBundle() throws Exception {
    try (var stream = Files.list(jar)) {
      return stream
          .filter(path -> path.getFileName().toString().startsWith("jahaziel-support-"))
          .filter(path -> path.getFileName().toString().endsWith(".zip"))
          .filter(Files::isRegularFile)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("no bundle found"));
    }
  }
}
