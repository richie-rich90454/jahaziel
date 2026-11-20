/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.snapshot.persistence.SnapshotEntity;
import dev.jahaziel.snapshot.persistence.SnapshotRepository;
import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.WorkspaceStorage;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

class SnapshotJobsCoverageGapTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir;

    private SnapshotServiceImpl service(
            SnapshotRepository snapshots, WorkspaceService workspaces, WorkspaceStorage storage) {
        return new SnapshotServiceImpl(
                snapshots, workspaces, storage, mock(AuditService.class), "/r");
    }

    @Test
    void createNullGuards() {
        SnapshotRepository snapshots = mock(SnapshotRepository.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        WorkspaceStorage storage = mock(WorkspaceStorage.class);
        SnapshotServiceImpl svc = service(snapshots, workspaces, storage);
        WorkspaceId wid = new WorkspaceId(UUID.randomUUID());
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.create(wid, null, SnapshotKind.AUTO, "u");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.create(wid, "t", SnapshotKind.AUTO, null);
                    }
                });
    }

    @Test
    void createComparesTwoRowsForParent() {
        SnapshotRepository snapshots = mock(SnapshotRepository.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        WorkspaceStorage storage = mock(WorkspaceStorage.class);
        SnapshotServiceImpl svc = service(snapshots, workspaces, storage);
        WorkspaceId wid = new WorkspaceId(UUID.randomUUID());
        dev.jahaziel.workspace.domain.Workspace ws =
                mock(dev.jahaziel.workspace.domain.Workspace.class);
        when(ws.id()).thenReturn(wid);
        when(ws.owner()).thenReturn(new UserId(UUID.randomUUID()));
        when(workspaces.get(wid)).thenReturn(ws);
        when(storage.snapshot(any(), any()))
                .thenReturn(new SnapshotHandle(UUID.randomUUID(), tempDir, Instant.now()));
        SnapshotEntity older = new SnapshotEntity();
        older.setSnapshotId(UUID.randomUUID());
        older.setWorkspaceId(wid.value());
        older.setTag("old");
        older.setKind("manual");
        older.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        older.setCreatedBy("u");
        older.setSnapshotPath("/s");
        SnapshotEntity newer = new SnapshotEntity();
        newer.setSnapshotId(UUID.randomUUID());
        newer.setWorkspaceId(wid.value());
        newer.setTag("mid");
        newer.setKind("manual");
        newer.setCreatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        newer.setCreatedBy("u");
        newer.setSnapshotPath("/s");
        when(snapshots.findByWorkspaceId(wid.value())).thenReturn(List.of(older, newer));
        SnapshotMetadata meta = svc.create(wid, "new", SnapshotKind.MANUAL, "u");
        assertTrue(meta.parentId().isPresent());
        assertEquals(newer.getSnapshotId(), meta.parentId().orElseThrow().value());
    }

    @Test
    void dailyCollectsTwoFailures() {
        SnapshotService snapshots = mock(SnapshotService.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        DailySnapshotJob job = new DailySnapshotJob(snapshots, workspaces);
        WorkspaceId firstId = new WorkspaceId(UUID.randomUUID());
        WorkspaceId secondId = new WorkspaceId(UUID.randomUUID());
        dev.jahaziel.workspace.domain.Workspace firstWs =
                mock(dev.jahaziel.workspace.domain.Workspace.class);
        when(firstWs.id()).thenReturn(firstId);
        dev.jahaziel.workspace.domain.Workspace secondWs =
                mock(dev.jahaziel.workspace.domain.Workspace.class);
        when(secondWs.id()).thenReturn(secondId);
        dev.jahaziel.workspace.api.WorkspaceView first =
                mock(dev.jahaziel.workspace.api.WorkspaceView.class);
        when(first.workspace()).thenReturn(firstWs);
        dev.jahaziel.workspace.api.WorkspaceView second =
                mock(dev.jahaziel.workspace.api.WorkspaceView.class);
        when(second.workspace()).thenReturn(secondWs);
        when(workspaces.describeAll()).thenReturn(List.of(first, second));
        when(snapshots.create(any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));
        assertThrows(
                RuntimeException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        job.run(JobContext.now());
                    }
                });
        verify(snapshots, times(2)).create(any(), any(), any(), any());
    }

    @Test
    void entityDeletedAtGetter() {
        SnapshotEntity row = new SnapshotEntity();
        Instant at = Instant.now();
        row.setDeletedAt(at);
        assertEquals(at, row.getDeletedAt());
    }

    @Test
    void snapshotTagBranches() {
        assertEquals("ok-1", new SnapshotTag("ok-1").value());
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new SnapshotTag(null);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new SnapshotTag("  ");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new SnapshotTag("a..b");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new SnapshotTag("a/b");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new SnapshotTag("a\\b");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new SnapshotTag(".hidden");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new SnapshotTag("/abs");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new SnapshotTag("C:\\win");
                    }
                });
    }
}
