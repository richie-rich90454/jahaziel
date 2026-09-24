/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VmIdleCheckJobCoverageGapTest {
  private Workspace fresh(WorkspaceId id) {
    return new Workspace(
        id,
        new UserId(UUID.randomUUID()),
        "c",
        new WorkspaceQuota(21474836480L, 1000000L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()), id, new VirtualMachineConfiguration(2, 2048)));
  }

  private Workspace idle(WorkspaceId id) {
    Workspace ws = fresh(id);
    ws.prepare();
    ws.boot();
    ws.markReady();
    ws.start();
    ws.markIdle(Instant.now());
    return ws;
  }

  private WorkspaceView view(Workspace ws) {
    return new WorkspaceView(ws, Instant.now(), Instant.now(), new StorageUsage(0, 0, 0, 0));
  }

  @Test
  void stopsIdleAndSkipsOthers() {
    var workspaces = mock(WorkspaceService.class);
    var runtime = mock(RuntimeService.class);
    WorkspaceId idleId = new WorkspaceId(UUID.randomUUID());
    WorkspaceId activeId = new WorkspaceId(UUID.randomUUID());
    when(workspaces.describeAll()).thenReturn(List.of(view(idle(idleId)), view(fresh(activeId))));
    new VmIdleCheckJob(workspaces, runtime).run(new JobContext(Instant.now()));
    verify(runtime, times(1)).stop(idleId);
    verify(runtime, never()).stop(activeId);
  }

  @Test
  void emptyFleetDoesNothing() {
    var workspaces = mock(WorkspaceService.class);
    var runtime = mock(RuntimeService.class);
    when(workspaces.describeAll()).thenReturn(List.of());
    new VmIdleCheckJob(workspaces, runtime).run(new JobContext(Instant.now()));
    verifyNoInteractions(runtime);
  }

  @Test
  void scheduleMetadata() {
    var job = new VmIdleCheckJob(mock(WorkspaceService.class), mock(RuntimeService.class));
    assertEquals(Duration.ofMinutes(1), job.period());
    assertEquals(JobScope.CLUSTER, job.scope());
  }

  @Test
  void constructorGuards() {
    assertThrows(
        NullPointerException.class, () -> new VmIdleCheckJob(null, mock(RuntimeService.class)));
    assertThrows(
        NullPointerException.class, () -> new VmIdleCheckJob(mock(WorkspaceService.class), null));
  }
}
