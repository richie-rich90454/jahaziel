/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.jail;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Validation plus start paths of {@link Jailer}. */
class JailerCoverageTest {
  @Test
  void constructorRejectsBlanks() {
    assertThrows(IllegalArgumentException.class, () -> new Jailer(null, "/f", "/j"));
    assertThrows(IllegalArgumentException.class, () -> new Jailer("  ", "/f", "/j"));
    assertThrows(IllegalArgumentException.class, () -> new Jailer("/jailer", null, "/j"));
    assertThrows(IllegalArgumentException.class, () -> new Jailer("/jailer", "", "/j"));
    assertThrows(IllegalArgumentException.class, () -> new Jailer("/jailer", "/f", null));
    assertThrows(IllegalArgumentException.class, () -> new Jailer("/jailer", "/f", " "));
  }

  @Test
  void jailCommandRejectsNulls() {
    Jailer jailer = new Jailer("/jailer", "/firecracker", "/jail");
    assertThrows(NullPointerException.class, () -> jailer.jailCommand(null, 1, 1, "/ns"));
    assertThrows(
        NullPointerException.class, () -> jailer.jailCommand(UUID.randomUUID(), 1, 1, null));
  }

  @Test
  void startMissingBinaryThrows() {
    Jailer jailer = new Jailer("/no-such-jailer-xyz", "/no-such-fc-xyz", "/jail");
    assertThrows(
        IllegalStateException.class,
        () -> jailer.start(UUID.randomUUID(), 100001, 100001, "/var/run/netns/x"));
  }

  @Test
  void startRealBinaryReturnsHandle(@TempDir Path root) {
    String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    Jailer jailer = new Jailer(javaBin, javaBin, root.toString());
    Process process = null;
    try {
      process = jailer.start(UUID.randomUUID(), 100001, 100001, "/ns");
      assertTrue(process.isAlive() || process.pid() > 0);
      assertTrue(process.toHandle().pid() > 0);
    } finally {
      if (process != null) {
        process.destroyForcibly();
        try {
          process.waitFor();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
}
