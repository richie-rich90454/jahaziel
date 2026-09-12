/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.snapshot.api.SnapshotNotFoundException;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.snapshot.persistence.SnapshotEntity;
import dev.jahaziel.snapshot.persistence.SnapshotRepository;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link SnapshotServiceImpl} with stubbed collaborators. */
class SnapshotServiceImplTest {
  private SnapshotRepository snapshots;
  private WorkspaceService workspaces;
  private WorkspaceStorage storage;
  private AuditService audit;
  private SnapshotServiceImpl service;
  private WorkspaceId workspaceId;
  private UserId ownerId;

  @BeforeEach
  void setUp() {
    snapshots = mock(SnapshotRepository.class);
    workspaces = mock(WorkspaceService.class);
    storage = mock(WorkspaceStorage.class);
    audit = mock(AuditService.class);
    service = new SnapshotServiceImpl(snapshots, workspaces, storage, audit, "/var/lib/jahaziel");
    workspaceId = new WorkspaceId(UUID.randomUUID());
    ownerId = new UserId(UUID.randomUUID());
  }

  @Test
  void createsSnapshotRow() {
    when(workspaces.get(workspaceId)).thenReturn(workspace());
    SnapshotHandle handle =
        new SnapshotHandle(UUID.randomUUID(), Path.of("/snaps/daily"), Instant.now());
    when(storage.snapshot(any(), any())).thenReturn(handle);
    when(snapshots.findByWorkspaceId(workspaceId.value())).thenReturn(List.of());
    SnapshotMetadata metadata = service.create(workspaceId, "daily", SnapshotKind.MANUAL, "user:x");
    assertEquals("daily", metadata.tag());
    assertEquals(SnapshotKind.MANUAL, metadata.kind());
    verify(snapshots).save(any(SnapshotEntity.class));
    verify(audit).append(any());
  }

  @Test
  void createRejectsBadInput() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(workspaceId, "  ", SnapshotKind.MANUAL, "u"));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(workspaceId, "t", SnapshotKind.MANUAL, "  "));
  }

  @Test
  void listsNewestFirst() {
    SnapshotEntity first = row("a", Instant.parse("2026-01-02T03:00:00Z"));
    SnapshotEntity second = row("b", Instant.parse("2026-01-03T03:00:00Z"));
    when(snapshots.findByWorkspaceId(workspaceId.value())).thenReturn(List.of(first, second));
    List<SnapshotMetadata> listed = service.list(workspaceId);
    assertEquals(2, listed.size());
    assertEquals("b", listed.get(0).tag());
  }

  @Test
  void missingSnapshotThrowsWithId() {
    SnapshotId id = new SnapshotId(UUID.randomUUID());
    when(snapshots.findById(id.value())).thenReturn(Optional.empty());
    assertThrows(SnapshotNotFoundException.class, () -> service.restore(workspaceId, id));
    assertThrows(SnapshotNotFoundException.class, () -> service.delete(id));
    verify(storage, never()).restore(any(), any());
  }

  @Test
  void restoreAndDeleteDelegate() {
    SnapshotEntity row = row("daily", Instant.now());
    when(snapshots.findById(any())).thenReturn(Optional.of(row));
    when(workspaces.get(workspaceId)).thenReturn(workspace());
    service.restore(workspaceId, new SnapshotId(UUID.randomUUID()));
    verify(storage).restore(any(), any());
    service.delete(new SnapshotId(UUID.randomUUID()));
    verify(storage).deleteSnapshot(any());
    verify(snapshots).delete(row);
  }

  private Workspace workspace() {
    Workspace workspace = mock(Workspace.class);
    when(workspace.id()).thenReturn(workspaceId);
    when(workspace.owner()).thenReturn(ownerId);
    return workspace;
  }

  private SnapshotEntity row(String tag, Instant at) {
    SnapshotEntity row = mock(SnapshotEntity.class);
    when(row.getSnapshotId()).thenReturn(UUID.randomUUID());
    when(row.getWorkspaceId()).thenReturn(workspaceId.value());
    when(row.getTag()).thenReturn(tag);
    when(row.getKind()).thenReturn("manual");
    when(row.getCreatedAt()).thenReturn(at);
    when(row.getCreatedBy()).thenReturn("user:x");
    when(row.getBtrfsSubvolumePath()).thenReturn("/snaps/" + tag);
    when(row.getLogicalBytes()).thenReturn(0L);
    when(row.getPhysicalBytes()).thenReturn(0L);
    when(row.getParentSnapshotId()).thenReturn(null);
    when(row.getSha256OfManifest()).thenReturn("");
    return row;
  }
}
