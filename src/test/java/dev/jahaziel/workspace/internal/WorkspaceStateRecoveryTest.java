/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class WorkspaceStateRecoveryTest {
  private final WorkspaceService workspaces = mock(WorkspaceService.class);
  private final WorkspaceStateRecovery recovery = new WorkspaceStateRecovery(workspaces);

  @Test
  void marksInterruptedActiveWorkspaceFailedWithRecoveryInstructions() {
    Workspace workspace = workspace();
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
    when(workspaces.describeAll()).thenReturn(List.of(view(workspace)));

    recovery.run(mock(ApplicationArguments.class));

    WorkspaceState.Failed failed = assertInstanceOf(WorkspaceState.Failed.class, workspace.state());
    assertTrue(
        failed
            .reason()
            .startsWith(
                "server restarted while workspace was starting or running; files are preserved, start to recover (operation "));
    String operation =
        failed.reason().substring(failed.reason().lastIndexOf("operation ") + "operation ".length());
    assertTrue(operation.endsWith(")"));
    UUID correlationId = UUID.fromString(operation.substring(0, operation.length() - 1));
    assertEquals(4, correlationId.version());
    verify(workspaces).save(workspace);
  }

  @Test
  void leavesRequestedWorkspaceReadyForItsFirstStart() {
    when(workspaces.describeAll()).thenReturn(List.of(view(workspace())));

    recovery.run(mock(ApplicationArguments.class));

    verify(workspaces, never()).save(any());
  }

  private static WorkspaceView view(Workspace workspace) {
    return new WorkspaceView(workspace, Instant.EPOCH, Instant.EPOCH, new StorageUsage(0, 0, 0, 0));
  }

  private static Workspace workspace() {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    return new Workspace(
        id,
        new UserId(UUID.randomUUID()),
        "recovery",
        WorkspaceQuota.defaults(),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()), id, new VirtualMachineConfiguration(2, 2048)));
  }
}
