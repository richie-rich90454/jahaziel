/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link AuditSpool} against a temporary directory. */
class AuditSpoolTest {
  @TempDir private Path root;

  @Test
  void appendsAndLists() throws Exception {
    AuditSpool spool = new AuditSpool(root);
    UUID workspace = UUID.randomUUID();
    spool.append(workspace, "{\"action\":\"login\"}");
    spool.append(workspace, "{\"action\":\"logout\"}");
    assertEquals(1, spool.spooled().size());
    Path file = spool.spooled().get(0);
    assertEquals(2, Files.readAllLines(file).size());
    assertTrue(file.toString().endsWith(".jsonl"));
  }

  @Test
  void blankLinesAreRejected() {
    AuditSpool spool = new AuditSpool(root);
    assertThrows(IllegalArgumentException.class, () -> spool.append(UUID.randomUUID(), "  "));
  }
}
