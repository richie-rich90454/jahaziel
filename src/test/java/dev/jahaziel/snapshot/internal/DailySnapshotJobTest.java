/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link DailySnapshotJob} with stubbed services. */
class DailySnapshotJobTest {
  @Test
  void snapshotsEveryWorkspace() {
    SnapshotService snapshots = mock(SnapshotService.class);
    WorkspaceService workspaces = mock(WorkspaceService.class);
    WorkspaceId firstId = new WorkspaceId(UUID.randomUUID());
    WorkspaceId secondId = new WorkspaceId(UUID.randomUUID());
    WorkspaceView first = view(firstId);
    WorkspaceView second = view(secondId);
    when(workspaces.describeAll()).thenReturn(List.of(first, second));
    new DailySnapshotJob(snapshots, workspaces)
        .run(new JobContext(Instant.parse("2026-01-02T02:00:00Z")));
    verify(snapshots).create(firstId, "auto-2026-01-02T02-00", SnapshotKind.AUTO, "system");
    verify(snapshots).create(secondId, "auto-2026-01-02T02-00", SnapshotKind.AUTO, "system");
  }

  @Test
  void scheduleIsDailyCluster() {
    DailySnapshotJob job =
        new DailySnapshotJob(mock(SnapshotService.class), mock(WorkspaceService.class));
    assertEquals(Duration.ofHours(24), job.period());
    assertEquals(JobScope.CLUSTER, job.scope());
  }

  private static WorkspaceView view(WorkspaceId id) {
    WorkspaceView view = mock(WorkspaceView.class);
    Workspace workspace = mock(Workspace.class);
    when(workspace.id()).thenReturn(id);
    when(view.workspace()).thenReturn(workspace);
    return view;
  }
}
