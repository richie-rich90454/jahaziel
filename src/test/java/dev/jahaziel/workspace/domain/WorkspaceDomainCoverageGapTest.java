/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.persistence.VirtualMachineEntity;
import dev.jahaziel.workspace.persistence.WorkspaceEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceDomainCoverageGapTest {
  private Workspace fresh(String name) {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    return new Workspace(
        id,
        new UserId(UUID.randomUUID()),
        name,
        new WorkspaceQuota(1L, 1L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()), id, new VirtualMachineConfiguration(1, 512)));
  }

  @Test
  void nullNameRejected() {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    UserId owner = new UserId(UUID.randomUUID());
    WorkspaceQuota quota = new WorkspaceQuota(1L, 1L);
    VirtualMachine vm =
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()), id, new VirtualMachineConfiguration(1, 512));
    assertThrows(IllegalArgumentException.class, () -> new Workspace(id, owner, null, quota, vm));
  }

  @Test
  void startFromIdle() {
    Workspace workspace = fresh("club");
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
    workspace.markIdle(Instant.now());
    workspace.start();
    assertEquals(WorkspaceState.Active.class, workspace.state().getClass());
  }

  @Test
  void abortBootFromPreparingReadyFailed() {
    Workspace preparing = fresh("a");
    preparing.prepare();
    preparing.abortBoot();
    assertEquals(WorkspaceState.Suspended.class, preparing.state().getClass());
    Workspace ready = fresh("b");
    ready.prepare();
    ready.boot();
    ready.markReady();
    ready.abortBoot();
    assertEquals(WorkspaceState.Suspended.class, ready.state().getClass());
    Workspace failed = fresh("c");
    failed.fail("boom");
    failed.abortBoot();
    assertEquals(WorkspaceState.Suspended.class, failed.state().getClass());
  }

  @Test
  void rebootFromFailed() {
    Workspace workspace = fresh("club");
    workspace.fail("boom");
    workspace.reboot();
    assertEquals(WorkspaceState.Requested.class, workspace.state().getClass());
  }

  @Test
  void failNullReasonRejected() {
    Workspace workspace = fresh("club");
    assertThrows(IllegalArgumentException.class, () -> workspace.fail(null));
  }

  @Test
  void virtualMachineWorkspaceId() {
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    VirtualMachine machine =
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()),
            workspaceId,
            new VirtualMachineConfiguration(1, 512));
    assertEquals(workspaceId, machine.workspaceId());
  }

  @Test
  void entityDeletedAtGetters() {
    WorkspaceEntity row = new WorkspaceEntity();
    Instant at = Instant.now();
    row.setDeletedAt(at);
    assertEquals(at, row.getDeletedAt());
    VirtualMachineEntity vm = new VirtualMachineEntity();
    vm.setDestroyedAt(at);
    assertEquals(at, vm.getDestroyedAt());
  }
}
