/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers {@link ProcessCommandRunner} without orphans. */
class ProcessCommandRunnerTest {
  @TempDir private Path root;

  private final ProcessCommandRunner runner = new ProcessCommandRunner();

  @Test
  void runEchoSucceeds() {
    String out = runner.run(shellCommand("echo hello"));
    assertEquals("hello", out);
  }

  @Test
  void runNonZeroThrowsWithOutput() {
    StorageException failure =
        assertThrows(StorageException.class, () -> runner.run(shellCommand("exit 1")));
    assertTrue(failure.getMessage().contains("exit 1"));
    assertEquals(1, failure.exitCode());
  }

  @Test
  void runMissingBinaryThrows() {
    StorageException failure =
        assertThrows(
            StorageException.class, () -> runner.run(List.of("jahaziel-no-such-binary-xyz")));
    assertEquals(-1, failure.exitCode());
    assertEquals("jahaziel-no-such-binary-xyz", failure.command());
    assertEquals("", failure.output());
  }

  @Test
  void runNullThrows() {
    assertThrows(NullPointerException.class, () -> runner.run(null));
  }

  @Test
  void existsNullThrows() {
    assertThrows(NullPointerException.class, () -> runner.exists(null));
  }

  @Test
  void existsMissingIsFalse() throws Exception {
    Path missing = root.resolve("missing-" + System.nanoTime());
    assertFalse(runner.exists(missing));
    Path file = root.resolve("f.txt");
    Files.writeString(file, "x");
    assertEquals(Files.exists(file), runner.exists(file));
  }

  @Test
  void exceptionCarriesFields() {
    StorageException viaExit = new StorageException("cmd", 3, "out");
    assertEquals("cmd", viaExit.command());
    assertEquals(3, viaExit.exitCode());
    assertEquals("out", viaExit.output());
    assertTrue(viaExit.getMessage().contains("cmd"));
    StorageException viaCause = new StorageException("cmd", new java.io.IOException("boom"));
    assertEquals("cmd", viaCause.command());
    assertEquals(-1, viaCause.exitCode());
    assertEquals("", viaCause.output());
  }

  @Test
  void interruptedRunRestoresFlag() throws Exception {
    Thread.currentThread().interrupt();
    try {
      assertThrows(StorageException.class, () -> runner.run(shellCommand("echo hi")));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void interruptedExistsReturnsFalse() {
    Thread.currentThread().interrupt();
    try {
      assertFalse(runner.exists(root.resolve("anything")));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  private static List<String> shellCommand(String command) {
    if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
      return List.of("cmd.exe", "/c", command);
    }
    return List.of("/bin/sh", "-c", command);
  }
}
