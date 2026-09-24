/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import dev.jahaziel.audit.api.AuditService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class AuditSpoolCoverageGapTest {
  @TempDir private Path root;

  @Test
  void drainUnreadableThrows() {
    AuditSpoolReader reader = new AuditSpoolReader(mock(AuditService.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> reader.drain(UUID.randomUUID(), root.resolve("missing.jsonl")));
    assertThrows(IllegalArgumentException.class, () -> reader.drain(UUID.randomUUID(), root));
  }

  @Test
  void drainDeleteFailureThrowsState() {
    AuditSpoolReader reader = new AuditSpoolReader(mock(AuditService.class));
    Path spool = root.resolve("ws.jsonl");
    try (MockedStatic<Files> files = mockStatic(Files.class)) {
      files.when(() -> Files.readAllLines(spool)).thenReturn(List.of("{\"action\":\"login\"}"));
      files.when(() -> Files.deleteIfExists(spool)).thenThrow(new IOException("locked"));
      assertThrows(IllegalStateException.class, () -> reader.drain(UUID.randomUUID(), spool));
    }
  }

  @Test
  void spooledUnreadableThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> AuditSpoolReader.spooled(root.resolve("no")));
    assertThrows(
        IllegalArgumentException.class, () -> AuditSpoolReader.spooled(root.resolve("file.txt")));
  }

  @Test
  void drainRejectsMissingAndNonStringAction() throws Exception {
    AuditSpoolReader reader = new AuditSpoolReader(mock(AuditService.class));
    Path missing = root.resolve("missing.jsonl");
    Files.writeString(missing, "{}\n");
    assertThrows(IllegalArgumentException.class, () -> reader.drain(UUID.randomUUID(), missing));
    Path number = root.resolve("number.jsonl");
    Files.writeString(number, "{\"action\":123}\n");
    assertThrows(IllegalArgumentException.class, () -> reader.drain(UUID.randomUUID(), number));
  }
}
