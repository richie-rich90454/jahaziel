/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.snapshot.api.SnapshotKinds;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.snapshot.persistence.SnapshotEntity;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link SnapshotMapper} and {@link SnapshotKinds} with stubbed rows. */
class SnapshotMapperTest {
  private final WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
  private final Instant at = Instant.parse("2026-01-02T03:04:05Z");

  @Test
  void roundTripPreservesValues() {
    SnapshotMetadata metadata =
        new SnapshotMetadata(
            new SnapshotId(UUID.randomUUID()),
            workspaceId,
            "daily",
            SnapshotKind.MANUAL,
            at,
            "user:x",
            Path.of("/snaps/daily"),
            100,
            50,
            Optional.empty(),
            "");
    SnapshotEntity row = SnapshotMapper.toEntity(metadata);
    assertEquals("manual", row.getKind());
    SnapshotMetadata back = SnapshotMapper.toDomain(row);
    assertEquals(metadata, back);
  }

  @Test
  void kindsMapBothWays() {
    assertEquals("auto", SnapshotKinds.toString(SnapshotKind.AUTO));
    assertEquals("pre-delete", SnapshotKinds.toString(SnapshotKind.PRE_DELETE));
    assertEquals(SnapshotKind.MANUAL, SnapshotKinds.fromString("manual"));
    assertThrows(IllegalStateException.class, () -> SnapshotKinds.fromString("bogus"));
  }

  @Test
  void toDomainReadsRow() {
    SnapshotEntity row = mock(SnapshotEntity.class);
    UUID id = UUID.randomUUID();
    when(row.getSnapshotId()).thenReturn(id);
    when(row.getWorkspaceId()).thenReturn(workspaceId.value());
    when(row.getTag()).thenReturn("daily");
    when(row.getKind()).thenReturn("auto");
    when(row.getCreatedAt()).thenReturn(at);
    when(row.getCreatedBy()).thenReturn("system");
    when(row.getSnapshotPath()).thenReturn("/snaps/daily");
    when(row.getLogicalBytes()).thenReturn(0L);
    when(row.getPhysicalBytes()).thenReturn(0L);
    when(row.getParentSnapshotId()).thenReturn(null);
    when(row.getSha256OfManifest()).thenReturn("");
    SnapshotMetadata metadata = SnapshotMapper.toDomain(row);
    assertEquals(new SnapshotId(id), metadata.id());
    assertEquals(SnapshotKind.AUTO, metadata.kind());
  }
}
