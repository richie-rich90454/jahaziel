/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.runtime.domain.VirtualMachine;
import dev.jahaziel.runtime.domain.VirtualMachineConfiguration;
import dev.jahaziel.runtime.domain.VirtualMachineId;
import dev.jahaziel.runtime.persistence.VirtualMachineEntity;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import dev.jahaziel.workspace.domain.WorkspaceState;
import dev.jahaziel.workspace.persistence.WorkspaceAclEntity;
import dev.jahaziel.workspace.persistence.WorkspaceEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link WorkspaceMapper} with stubbed rows. */
class WorkspaceMapperTest {
  private final WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
  private final UserId owner = new UserId(UUID.randomUUID());
  private final Instant at = Instant.now();

  @Test
  void toEntityMapsDomainFields() {
    Workspace workspace = newWorkspace();
    workspace.prepare();
    WorkspaceEntity row = WorkspaceMapper.toEntity(workspace, "/home", "/jail");
    assertEquals(workspaceId.value(), row.getWorkspaceId());
    assertEquals("preparing", row.getState());
    assertEquals("club", row.getName());
    assertEquals(21474836480L, row.getLogicalQuotaBytes());
  }

  @Test
  void toMachineEntityMapsSizing() {
    VirtualMachine machine =
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()),
            workspaceId,
            new VirtualMachineConfiguration(4, 4096));
    VirtualMachineEntity row =
        WorkspaceMapper.toMachineEntity(machine, workspaceId, "node-a", "/home", "/jail");
    assertEquals("stopped", row.getState());
    assertEquals(4, row.getVcpuCount());
    assertEquals(4096, row.getMemoryMib());
    assertEquals("node-a", row.getNodeId());
  }

  @Test
  void toDomainReplaysStates() {
    assertInstanceOf(WorkspaceState.Requested.class, domain("requested").state());
    assertInstanceOf(WorkspaceState.Preparing.class, domain("preparing").state());
    assertInstanceOf(WorkspaceState.Booting.class, domain("booting").state());
    assertInstanceOf(WorkspaceState.Ready.class, domain("ready").state());
    assertInstanceOf(WorkspaceState.Active.class, domain("active").state());
    assertInstanceOf(WorkspaceState.Suspended.class, domain("suspended").state());
    assertInstanceOf(WorkspaceState.Destroyed.class, domain("destroyed").state());
    WorkspaceState.Failed failed =
        assertInstanceOf(WorkspaceState.Failed.class, domain("failed").state());
    assertEquals("not recorded", failed.reason());
  }

  @Test
  void toDomainFoldsGrantsAndSnapshots() {
    WorkspaceAclEntity grant = mock(WorkspaceAclEntity.class);
    WorkspaceAclEntity.Id key = mock(WorkspaceAclEntity.Id.class);
    UUID grantee = UUID.randomUUID();
    when(grant.getId()).thenReturn(key);
    when(key.getGranteeUserId()).thenReturn(grantee);
    when(key.getPermission()).thenReturn("read");
    SnapshotId snapshot = new SnapshotId(UUID.randomUUID());
    Workspace workspace =
        WorkspaceMapper.toDomain(row("requested"), List.of(grant), machineRow(), List.of(snapshot));
    assertEquals(List.of(snapshot), workspace.snapshots());
    assertEquals("club", workspace.name());
  }

  @Test
  void unknownStateThrows() {
    assertThrows(
        IllegalStateException.class,
        () -> WorkspaceMapper.toDomain(row("bogus"), List.of(), machineRow(), List.of()));
  }

  @Test
  void stateToStringCoversAllVariants() {
    assertEquals("requested", WorkspaceMapper.stateToString(new WorkspaceState.Requested(at)));
    assertEquals("preparing", WorkspaceMapper.stateToString(new WorkspaceState.Preparing(at)));
    assertEquals("booting", WorkspaceMapper.stateToString(new WorkspaceState.Booting(at)));
    assertEquals("ready", WorkspaceMapper.stateToString(new WorkspaceState.Ready(at)));
    assertEquals("active", WorkspaceMapper.stateToString(new WorkspaceState.Active(at)));
    assertEquals("idle", WorkspaceMapper.stateToString(new WorkspaceState.Idle(at, at)));
    assertEquals("suspended", WorkspaceMapper.stateToString(new WorkspaceState.Suspended(at)));
    assertEquals("destroyed", WorkspaceMapper.stateToString(new WorkspaceState.Destroyed(at)));
    assertEquals("failed", WorkspaceMapper.stateToString(new WorkspaceState.Failed(at, "x")));
  }

  private Workspace domain(String state) {
    return WorkspaceMapper.toDomain(row(state), List.of(), machineRow(), List.of());
  }

  private WorkspaceEntity row(String state) {
    WorkspaceEntity row = mock(WorkspaceEntity.class);
    when(row.getWorkspaceId()).thenReturn(workspaceId.value());
    when(row.getOwnerUserId()).thenReturn(owner.value());
    when(row.getName()).thenReturn("club");
    when(row.getState()).thenReturn(state);
    when(row.getStateChangedAt()).thenReturn(at);
    when(row.getLogicalQuotaBytes()).thenReturn(21474836480L);
    when(row.getInodeQuota()).thenReturn(1000000L);
    return row;
  }

  private VirtualMachineEntity machineRow() {
    VirtualMachineEntity machine = mock(VirtualMachineEntity.class);
    when(machine.getVmId()).thenReturn(UUID.randomUUID());
    when(machine.getVcpuCount()).thenReturn(2);
    when(machine.getMemoryMib()).thenReturn(2048L);
    return machine;
  }

  private Workspace newWorkspace() {
    return new Workspace(
        workspaceId,
        owner,
        "club",
        new WorkspaceQuota(21474836480L, 1000000L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()),
            workspaceId,
            new VirtualMachineConfiguration(2, 2048)));
  }
}
