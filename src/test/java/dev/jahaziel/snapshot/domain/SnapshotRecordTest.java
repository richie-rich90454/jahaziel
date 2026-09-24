/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.snapshot.api.SnapshotKinds;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Validation of the snapshot records and stored-kind mapping. */
class SnapshotRecordTest {
  private SnapshotMetadata valid() {
    return new SnapshotMetadata(
        new SnapshotId(UUID.randomUUID()),
        new WorkspaceId(UUID.randomUUID()),
        "daily",
        SnapshotKind.MANUAL,
        Instant.now(),
        "user",
        Path.of("snap"),
        10L,
        8L,
        Optional.empty(),
        "abc");
  }

  @Test
  void metadataStoresFields() {
    SnapshotMetadata metadata = valid();
    assertEquals("daily", metadata.tag());
    assertEquals(SnapshotKind.MANUAL, metadata.kind());
    assertEquals(10L, metadata.logicalBytes());
    assertEquals(Optional.empty(), metadata.parentId());
  }

  @Test
  void metadataGuardsTagAndCreator() {
    SnapshotMetadata probe = valid();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SnapshotMetadata(
                probe.id(),
                probe.workspaceId(),
                null,
                probe.kind(),
                probe.createdAt(),
                probe.createdBy(),
                probe.snapshotPath(),
                0,
                0,
                probe.parentId(),
                probe.manifestSha256()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SnapshotMetadata(
                probe.id(),
                probe.workspaceId(),
                "  ",
                probe.kind(),
                probe.createdAt(),
                probe.createdBy(),
                probe.snapshotPath(),
                0,
                0,
                probe.parentId(),
                probe.manifestSha256()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SnapshotMetadata(
                probe.id(),
                probe.workspaceId(),
                probe.tag(),
                probe.kind(),
                probe.createdAt(),
                null,
                probe.snapshotPath(),
                0,
                0,
                probe.parentId(),
                probe.manifestSha256()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SnapshotMetadata(
                probe.id(),
                probe.workspaceId(),
                probe.tag(),
                probe.kind(),
                probe.createdAt(),
                "  ",
                probe.snapshotPath(),
                0,
                0,
                probe.parentId(),
                probe.manifestSha256()));
  }

  @Test
  void metadataRejectsNegativeSizes() {
    SnapshotMetadata probe = valid();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SnapshotMetadata(
                probe.id(),
                probe.workspaceId(),
                probe.tag(),
                probe.kind(),
                probe.createdAt(),
                probe.createdBy(),
                probe.snapshotPath(),
                -1,
                0,
                probe.parentId(),
                probe.manifestSha256()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SnapshotMetadata(
                probe.id(),
                probe.workspaceId(),
                probe.tag(),
                probe.kind(),
                probe.createdAt(),
                probe.createdBy(),
                probe.snapshotPath(),
                0,
                -1,
                probe.parentId(),
                probe.manifestSha256()));
  }

  @Test
  void manualBuildsPendingRecord() {
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    SnapshotMetadata metadata =
        SnapshotMetadata.manual(workspaceId, "daily", "user", Path.of("snap"));
    assertEquals(workspaceId, metadata.workspaceId());
    assertEquals(SnapshotKind.MANUAL, metadata.kind());
    assertEquals(0L, metadata.logicalBytes());
    assertEquals("pending", metadata.manifestSha256());
    assertTrue(metadata.parentId().isEmpty());
  }

  @Test
  void kindsRoundTrip() {
    assertEquals("auto", SnapshotKinds.toString(SnapshotKind.AUTO));
    assertEquals("manual", SnapshotKinds.toString(SnapshotKind.MANUAL));
    assertEquals("pre-delete", SnapshotKinds.toString(SnapshotKind.PRE_DELETE));
    assertEquals(SnapshotKind.AUTO, SnapshotKinds.fromString("auto"));
    assertEquals(SnapshotKind.MANUAL, SnapshotKinds.fromString("manual"));
    assertEquals(SnapshotKind.PRE_DELETE, SnapshotKinds.fromString("pre-delete"));
    assertThrows(IllegalStateException.class, () -> SnapshotKinds.fromString("bogus"));
  }
}
