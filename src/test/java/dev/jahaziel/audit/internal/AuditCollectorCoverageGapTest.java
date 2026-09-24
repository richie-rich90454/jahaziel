/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditCollectorCoverageGapTest {
  @TempDir private Path root;

  @Test
  void runDrainsValidAndQuarantinesInvalid() throws Exception {
    AuditSpoolReader reader = mock(AuditSpoolReader.class);
    Path staging = root.resolve("staging");
    Files.createDirectories(staging);
    UUID workspace = UUID.randomUUID();
    Path valid = staging.resolve(workspace + "-2026-01-01.jsonl");
    Files.writeString(valid, "{\"action\":\"x\"}\n");
    Path shortName = staging.resolve("short.jsonl");
    Files.writeString(shortName, "{}\n");
    Path badUuid = staging.resolve("zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz.jsonl");
    Files.writeString(badUuid, "{}\n");
    when(reader.drain(any(), any())).thenReturn(1);
    new AuditCollectorJob(reader, staging).run(new JobContext(Instant.now()));
    verify(reader).drain(workspace, valid);
    verify(reader, never()).drain(any(), org.mockito.ArgumentMatchers.eq(shortName));
    assertTrue(Files.exists(valid));
    assertTrue(Files.notExists(shortName));
    assertTrue(Files.exists(staging.resolve("short.jsonl.bad")));
    assertTrue(Files.exists(staging.resolve(badUuid.getFileName() + ".bad")));
  }

  @Test
  void runEmptyDirDoesNothing() throws Exception {
    AuditSpoolReader reader = mock(AuditSpoolReader.class);
    Path staging = root.resolve("empty");
    Files.createDirectories(staging);
    new AuditCollectorJob(reader, staging).run(new JobContext(Instant.now()));
    verify(reader, never()).drain(any(), any());
  }

  @Test
  void runCreatesMissingDir() {
    AuditSpoolReader reader = mock(AuditSpoolReader.class);
    Path staging = root.resolve("missing");
    new AuditCollectorJob(reader, staging).run(new JobContext(Instant.now()));
    assertTrue(Files.isDirectory(staging));
  }

  @Test
  void runFailsWhenStagingIsAFile() throws Exception {
    AuditSpoolReader reader = mock(AuditSpoolReader.class);
    Path file = root.resolve("file");
    Files.writeString(file, "x");
    assertThrows(
        IllegalStateException.class,
        () -> new AuditCollectorJob(reader, file).run(new JobContext(Instant.now())));
  }

  @Test
  void quarantineFailureThrowsState() throws Exception {
    AuditSpoolReader reader = mock(AuditSpoolReader.class);
    Path staging = root.resolve("locked");
    Files.createDirectories(staging);
    UUID workspace = UUID.randomUUID();
    Path spool = staging.resolve(workspace + "-2026-01-01.jsonl");
    Files.writeString(spool, "{}\n");
    Path badDir = staging.resolve(spool.getFileName() + ".bad");
    Files.createDirectory(badDir);
    Files.writeString(badDir.resolve("probe"), "x");
    when(reader.drain(any(), any())).thenThrow(new IllegalArgumentException("bad line"));
    assertThrows(
        IllegalStateException.class,
        () -> new AuditCollectorJob(reader, staging).run(new JobContext(Instant.now())));
  }

  @Test
  void periodAndScope() {
    AuditCollectorJob job = new AuditCollectorJob(mock(AuditSpoolReader.class), root);
    assertEquals(Duration.ofMinutes(1), job.period());
    assertEquals(JobScope.NODE, job.scope());
  }
}
