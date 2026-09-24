/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.snapshot.api.SnapshotKinds;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.snapshot.internal.DailySnapshotJob;
import dev.jahaziel.snapshot.internal.SnapshotMapper;
import dev.jahaziel.snapshot.internal.SnapshotRetentionJob;
import dev.jahaziel.snapshot.internal.SnapshotServiceImpl;
import dev.jahaziel.snapshot.persistence.SnapshotEntity;
import dev.jahaziel.snapshot.persistence.SnapshotRepository;
import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnapshotExtraCoverageTest {
  @Test
  void kindsRoundTripAndUnknown() {
    assertEquals("auto", SnapshotKinds.toString(SnapshotKind.AUTO));
    assertEquals("manual", SnapshotKinds.toString(SnapshotKind.MANUAL));
    assertEquals("pre-delete", SnapshotKinds.toString(SnapshotKind.PRE_DELETE));
    assertEquals(SnapshotKind.AUTO, SnapshotKinds.fromString("auto"));
    assertEquals(SnapshotKind.MANUAL, SnapshotKinds.fromString("manual"));
    assertEquals(SnapshotKind.PRE_DELETE, SnapshotKinds.fromString("pre-delete"));
    assertThrows(IllegalStateException.class, () -> SnapshotKinds.fromString("nope"));
  }

  @Test
  void mapperParentBranches() {
    SnapshotEntity row = new SnapshotEntity();
    UUID sid = UUID.randomUUID();
    UUID wid = UUID.randomUUID();
    row.setSnapshotId(sid);
    row.setWorkspaceId(wid);
    row.setTag("t");
    row.setKind("auto");
    row.setCreatedAt(Instant.now());
    row.setCreatedBy("u");
    row.setSnapshotPath("/s");
    row.setParentSnapshotId(null);
    row.setSha256OfManifest("");
    assertTrue(SnapshotMapper.toDomain(row).parentId().isEmpty());
    row.setParentSnapshotId(UUID.randomUUID());
    assertTrue(SnapshotMapper.toDomain(row).parentId().isPresent());
    SnapshotMetadata withParent =
        new SnapshotMetadata(
            new SnapshotId(sid),
            new WorkspaceId(wid),
            "t",
            SnapshotKind.AUTO,
            Instant.now(),
            "u",
            java.nio.file.Path.of("/s"),
            0,
            0,
            Optional.of(new SnapshotId(UUID.randomUUID())),
            "");
    assertTrue(SnapshotMapper.toEntity(withParent).getParentSnapshotId() != null);
    SnapshotMetadata noParent =
        new SnapshotMetadata(
            new SnapshotId(sid),
            new WorkspaceId(wid),
            "t",
            SnapshotKind.AUTO,
            Instant.now(),
            "u",
            java.nio.file.Path.of("/s"),
            0,
            0,
            Optional.empty(),
            "");
    assertEquals(null, SnapshotMapper.toEntity(noParent).getParentSnapshotId());
  }

  @Test
  void serviceCtorAndNullGuards() {
    var snapshots = mock(SnapshotRepository.class);
    var workspaces = mock(WorkspaceService.class);
    var storage = mock(WorkspaceStorage.class);
    var audit = mock(AuditService.class);
    assertThrows(
        NullPointerException.class,
        () -> new SnapshotServiceImpl(null, workspaces, storage, audit, "/r"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SnapshotServiceImpl(snapshots, workspaces, storage, audit, " "));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SnapshotServiceImpl(snapshots, workspaces, storage, audit, null));
    var svc = new SnapshotServiceImpl(snapshots, workspaces, storage, audit, "/r");
    WorkspaceId wid = new WorkspaceId(UUID.randomUUID());
    assertThrows(NullPointerException.class, () -> svc.create(null, "t", SnapshotKind.AUTO, "u"));
    assertThrows(NullPointerException.class, () -> svc.create(wid, "t", null, "u"));
    assertThrows(NullPointerException.class, () -> svc.list(null));
  }

  @Test
  void restoreRejectsForeignWorkspace() {
    var snapshots = mock(SnapshotRepository.class);
    var workspaces = mock(WorkspaceService.class);
    var storage = mock(WorkspaceStorage.class);
    var audit = mock(AuditService.class);
    var svc = new SnapshotServiceImpl(snapshots, workspaces, storage, audit, "/r");
    SnapshotEntity row = new SnapshotEntity();
    row.setSnapshotId(UUID.randomUUID());
    row.setWorkspaceId(UUID.randomUUID());
    row.setTag("t");
    row.setKind("manual");
    row.setCreatedAt(Instant.now());
    row.setCreatedBy("u");
    row.setSnapshotPath("/s");
    when(snapshots.findById(any())).thenReturn(Optional.of(row));
    assertThrows(
        IllegalArgumentException.class,
        () -> svc.restore(new WorkspaceId(UUID.randomUUID()), new SnapshotId(row.getSnapshotId())));
  }

  @Test
  void createLinksParent() {
    var snapshots = mock(SnapshotRepository.class);
    var workspaces = mock(WorkspaceService.class);
    var storage = mock(WorkspaceStorage.class);
    var audit = mock(AuditService.class);
    var svc = new SnapshotServiceImpl(snapshots, workspaces, storage, audit, "/r");
    WorkspaceId wid = new WorkspaceId(UUID.randomUUID());
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    when(ws.id()).thenReturn(wid);
    when(ws.owner()).thenReturn(new dev.jahaziel.identity.domain.UserId(UUID.randomUUID()));
    when(workspaces.get(wid)).thenReturn(ws);
    when(storage.snapshot(any(), any()))
        .thenReturn(
            new SnapshotHandle(UUID.randomUUID(), java.nio.file.Path.of("/s"), Instant.now()));
    SnapshotEntity older = new SnapshotEntity();
    older.setSnapshotId(UUID.randomUUID());
    older.setWorkspaceId(wid.value());
    older.setTag("old");
    older.setKind("manual");
    older.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    older.setCreatedBy("u");
    older.setSnapshotPath("/s");
    when(snapshots.findByWorkspaceId(wid.value())).thenReturn(List.of(older));
    var meta = svc.create(wid, "new", SnapshotKind.MANUAL, "u");
    assertTrue(meta.parentId().isPresent());
  }

  @Test
  void retentionEnforcesCountsAndAge() {
    var snapshots = mock(dev.jahaziel.snapshot.api.SnapshotService.class);
    var workspaces = mock(WorkspaceService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
    SnapshotRetentionJob job = new SnapshotRetentionJob(snapshots, workspaces, 1, 30, clock);
    WorkspaceId wid = new WorkspaceId(UUID.randomUUID());
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    when(ws.id()).thenReturn(wid);
    var view = mock(dev.jahaziel.workspace.api.WorkspaceView.class);
    when(view.workspace()).thenReturn(ws);
    when(workspaces.describeAll()).thenReturn(List.of(view));
    SnapshotMetadata auto1 =
        new SnapshotMetadata(
            new SnapshotId(UUID.randomUUID()),
            wid,
            "a1",
            SnapshotKind.AUTO,
            Instant.parse("2026-05-01T00:00:00Z"),
            "s",
            java.nio.file.Path.of("/a"),
            0,
            0,
            Optional.empty(),
            "");
    SnapshotMetadata auto2 =
        new SnapshotMetadata(
            new SnapshotId(UUID.randomUUID()),
            wid,
            "a2",
            SnapshotKind.AUTO,
            Instant.parse("2026-05-02T00:00:00Z"),
            "s",
            java.nio.file.Path.of("/a"),
            0,
            0,
            Optional.empty(),
            "");
    SnapshotMetadata oldPre =
        new SnapshotMetadata(
            new SnapshotId(UUID.randomUUID()),
            wid,
            "p1",
            SnapshotKind.PRE_DELETE,
            Instant.parse("2026-01-01T00:00:00Z"),
            "s",
            java.nio.file.Path.of("/a"),
            0,
            0,
            Optional.empty(),
            "");
    SnapshotMetadata freshPre =
        new SnapshotMetadata(
            new SnapshotId(UUID.randomUUID()),
            wid,
            "p2",
            SnapshotKind.PRE_DELETE,
            Instant.parse("2026-05-30T00:00:00Z"),
            "s",
            java.nio.file.Path.of("/a"),
            0,
            0,
            Optional.empty(),
            "");
    SnapshotMetadata manual =
        new SnapshotMetadata(
            new SnapshotId(UUID.randomUUID()),
            wid,
            "m",
            SnapshotKind.MANUAL,
            Instant.parse("2020-01-01T00:00:00Z"),
            "s",
            java.nio.file.Path.of("/a"),
            0,
            0,
            Optional.empty(),
            "");
    when(snapshots.list(wid)).thenReturn(List.of(auto1, auto2, oldPre, freshPre, manual));
    job.run(JobContext.now());
    verify(snapshots, times(1)).delete(auto1.id());
    verify(snapshots, times(1)).delete(oldPre.id());
    assertEquals(Duration.ofHours(24), job.period());
    assertEquals(JobScope.CLUSTER, job.scope());
    assertThrows(
        IllegalArgumentException.class,
        () -> new SnapshotRetentionJob(snapshots, workspaces, 0, 1, clock));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SnapshotRetentionJob(snapshots, workspaces, 1, 0, clock));
    assertThrows(
        NullPointerException.class, () -> new SnapshotRetentionJob(null, workspaces, 1, 1, clock));
  }

  @Test
  void dailySnapshotsCollectFirstFailure() {
    var snapshots = mock(dev.jahaziel.snapshot.api.SnapshotService.class);
    var workspaces = mock(WorkspaceService.class);
    DailySnapshotJob job = new DailySnapshotJob(snapshots, workspaces);
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    WorkspaceId wid = new WorkspaceId(UUID.randomUUID());
    when(ws.id()).thenReturn(wid);
    var view = mock(dev.jahaziel.workspace.api.WorkspaceView.class);
    when(view.workspace()).thenReturn(ws);
    when(workspaces.describeAll()).thenReturn(List.of(view));
    when(snapshots.create(any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));
    assertThrows(RuntimeException.class, () -> job.run(JobContext.now()));
    when(workspaces.describeAll()).thenReturn(List.of());
    job.run(JobContext.now());
    assertEquals(Duration.ofHours(24), job.period());
    assertEquals(JobScope.CLUSTER, job.scope());
    assertThrows(NullPointerException.class, () -> new DailySnapshotJob(null, workspaces));
    assertThrows(NullPointerException.class, () -> new DailySnapshotJob(snapshots, null));
  }

  @Test
  void snapshotNotFoundCarriesId() {
    SnapshotId id = new SnapshotId(UUID.randomUUID());
    var ex = new dev.jahaziel.snapshot.api.SnapshotNotFoundException(id);
    assertTrue(ex.getMessage().contains(id.value().toString()));
    var missing = new dev.jahaziel.snapshot.api.SnapshotNotFoundException(null);
    assertNull(missing.snapshotId());
  }
}
