/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.jahaziel.audit.api.AuditService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link AuditSpoolReader} against a temporary directory. */
class AuditSpoolReaderTest {
  @TempDir private Path root;

  @Test
  void drainsLinesAndDeletesFile() throws Exception {
    AuditService audit = mock(AuditService.class);
    AuditSpoolReader reader = new AuditSpoolReader(audit);
    Path spool = root.resolve("ws.jsonl");
    Files.writeString(spool, "{\"action\":\"login\"}\n\n{\"action\":\"logout\"}\n");
    int ingested = reader.drain(UUID.randomUUID(), spool);
    assertEquals(2, ingested);
    verify(audit, times(2)).append(any());
    assertEquals(false, Files.exists(spool));
  }

  @Test
  void rejectsMalformedLines() throws Exception {
    AuditService audit = mock(AuditService.class);
    AuditSpoolReader reader = new AuditSpoolReader(audit);
    Path spool = root.resolve("ws.jsonl");
    Files.writeString(spool, "not json\n");
    UUID workspace = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> reader.drain(workspace, spool));
    verify(audit, never()).append(any());
  }

  @Test
  void listsSpooledFiles() throws Exception {
    Files.writeString(root.resolve("a.jsonl"), "{}\n");
    Files.writeString(root.resolve("b.txt"), "x");
    assertEquals(1, AuditSpoolReader.spooled(root).size());
  }
}
