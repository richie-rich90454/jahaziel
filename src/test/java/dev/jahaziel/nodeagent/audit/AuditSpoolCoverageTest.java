/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Edge branches of {@link AuditSpool}. */
class AuditSpoolCoverageTest {
  @Test
  void constructorRejectsNull() {
    assertThrows(NullPointerException.class, () -> new AuditSpool(null));
  }

  @Test
  void appendRejectsNull(@TempDir Path root) {
    AuditSpool spool = new AuditSpool(root);
    assertThrows(NullPointerException.class, () -> spool.append(null, "{}"));
    assertThrows(IllegalArgumentException.class, () -> spool.append(UUID.randomUUID(), null));
    assertThrows(IllegalArgumentException.class, () -> spool.append(UUID.randomUUID(), ""));
  }

  @Test
  void spooledEmptyBeforeFirstAppend(@TempDir Path root) {
    AuditSpool spool = new AuditSpool(root.resolve("absent"));
    assertTrue(spool.spooled().isEmpty());
  }

  @Test
  void spooledIgnoresNonJsonl(@TempDir Path root) throws Exception {
    AuditSpool spool = new AuditSpool(root);
    spool.append(UUID.randomUUID(), "{\"a\":1}");
    Files.writeString(root.resolve("notes.txt"), "x");
    assertEquals(1, spool.spooled().size());
  }

  @Test
  void appendFailsWhenStagingIsFile(@TempDir Path root) throws Exception {
    Path file = root.resolve("block");
    Files.writeString(file, "x");
    AuditSpool spool = new AuditSpool(file);
    assertThrows(UncheckedIOException.class, () -> spool.append(UUID.randomUUID(), "{}"));
  }
}
