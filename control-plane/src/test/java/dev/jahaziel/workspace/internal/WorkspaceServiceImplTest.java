/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.UserNotFoundException;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.runtime.persistence.VirtualMachineEntity;
import dev.jahaziel.runtime.persistence.VirtualMachineRepository;
import dev.jahaziel.snapshot.persistence.SnapshotRepository;
import dev.jahaziel.spi.UserIdentity;
import dev.jahaziel.workspace.api.WorkspaceNotFoundException;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;
import dev.jahaziel.workspace.persistence.WorkspaceAclRepository;
import dev.jahaziel.workspace.persistence.WorkspaceEntity;
import dev.jahaziel.workspace.persistence.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Behavior of {@link WorkspaceServiceImpl} with stubbed rows. */
class WorkspaceServiceImplTest {
  private WorkspaceRepository workspaces;
  private WorkspaceAclRepository acl;
  private VirtualMachineRepository machines;
  private SnapshotRepository snapshots;
  private UserService users;
  private WorkspaceServiceImpl service;
  private UserId owner;
  private NodeId nodeId;

  @BeforeEach
  void setUp() {
    workspaces = mock(WorkspaceRepository.class);
    acl = mock(WorkspaceAclRepository.class);
    machines = mock(VirtualMachineRepository.class);
    snapshots = mock(SnapshotRepository.class);
    users = mock(UserService.class);
    service =
        new WorkspaceServiceImpl(workspaces, acl, machines, snapshots, users, "/var/lib/jahaziel");
    owner = new UserId(UUID.randomUUID());
    nodeId = new NodeId("node-a");
  }

  @Test
  void createsRequestedWorkspaceWithPaths() {
    when(users.get(owner)).thenReturn(new UserIdentity("sub", "a@b.c", "A", List.of(), List.of()));
    Workspace workspace = service.create(owner, "site", 2, 2048, nodeId);
    assertInstanceOf(WorkspaceState.Requested.class, workspace.state());
    assertEquals("site", workspace.name());
    ArgumentCaptor<WorkspaceEntity> workspaceCaptor =
        ArgumentCaptor.forClass(WorkspaceEntity.class);
    verify(workspaces).save(workspaceCaptor.capture());
    assertEquals("requested", workspaceCaptor.getValue().getState());
    assertEquals(21474836480L, workspaceCaptor.getValue().getLogicalQuotaBytes());
    ArgumentCaptor<VirtualMachineEntity> machineCaptor =
        ArgumentCaptor.forClass(VirtualMachineEntity.class);
    verify(machines).save(machineCaptor.capture());
    String disk = "/var/lib/jahaziel/jail/" + workspace.id().value() + "/rootfs.img";
    assertEquals(disk, machineCaptor.getValue().getDiskPath());
    assertEquals("node-a", machineCaptor.getValue().getNodeId());
    assertEquals(
        "/var/lib/jahaziel/workspaces/" + owner.value() + "/home",
        machineCaptor.getValue().getHomePath());
  }

  @Test
  void createRequiresKnownOwner() {
    when(users.get(owner)).thenThrow(new UserNotFoundException(owner));
    assertThrows(UserNotFoundException.class, () -> service.create(owner, "site", 2, 2048, nodeId));
    verify(workspaces, never()).save(any());
  }

  @Test
  void createRejectsBadInput() {
    UserIdentity identity = new UserIdentity("sub", "a@b.c", "A", List.of(), List.of());
    when(users.get(owner)).thenReturn(identity);
    assertThrows(
        IllegalArgumentException.class, () -> service.create(owner, "  ", 2, 2048, nodeId));
    assertThrows(
        IllegalArgumentException.class, () -> service.create(owner, "site", 0, 2048, nodeId));
    assertThrows(IllegalArgumentException.class, () -> service.create(owner, "site", 2, 0, nodeId));
  }

  @Test
  void missingWorkspaceThrowsWithId() {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    when(workspaces.findById(id.value())).thenReturn(Optional.empty());
    WorkspaceNotFoundException failure =
        assertThrows(WorkspaceNotFoundException.class, () -> service.get(id));
    assertEquals(id, failure.workspaceId());
  }

  @Test
  void getRebuildsActiveWorkspace() {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    stubRows(id, "active");
    Workspace workspace = service.get(id);
    assertInstanceOf(WorkspaceState.Active.class, workspace.state());
    assertTrue(workspace.isOwnedBy(owner));
  }

  @Test
  void deleteDestroysAndRemovesRow() {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    stubRows(id, "active");
    service.delete(id);
    verify(workspaces).deleteById(id.value());
  }

  @Test
  void deleteMissingThrows() {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    when(workspaces.findById(id.value())).thenReturn(Optional.empty());
    assertThrows(WorkspaceNotFoundException.class, () -> service.delete(id));
  }

  @Test
  void describeByOwnerMapsRows() {
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    WorkspaceEntity row = mock(WorkspaceEntity.class);
    when(row.getWorkspaceId()).thenReturn(id.value());
    when(row.getCreatedAt()).thenReturn(Instant.now());
    when(row.getStateChangedAt()).thenReturn(Instant.now());
    when(workspaces.findByOwnerUserId(owner.value())).thenReturn(List.of(row));
    stubRows(id, "ready");
    List<WorkspaceView> views = service.describeByOwner(owner);
    assertEquals(1, views.size());
    assertEquals(id, views.get(0).workspace().id());
  }

  private void stubRows(WorkspaceId id, String state) {
    Instant at = Instant.now();
    WorkspaceEntity row = mock(WorkspaceEntity.class);
    when(row.getWorkspaceId()).thenReturn(id.value());
    when(row.getOwnerUserId()).thenReturn(owner.value());
    when(row.getName()).thenReturn("site");
    when(row.getState()).thenReturn(state);
    when(row.getStateChangedAt()).thenReturn(at);
    when(row.getLogicalQuotaBytes()).thenReturn(21474836480L);
    when(row.getInodeQuota()).thenReturn(1000000L);
    when(row.getCreatedAt()).thenReturn(at);
    when(workspaces.findById(id.value())).thenReturn(Optional.of(row));
    when(acl.findByIdWorkspaceId(id.value())).thenReturn(List.of());
    VirtualMachineEntity machine = mock(VirtualMachineEntity.class);
    when(machine.getVmId()).thenReturn(UUID.randomUUID());
    when(machine.getVcpuCount()).thenReturn(2);
    when(machine.getMemoryMib()).thenReturn(2048L);
    when(machines.findByWorkspaceId(id.value())).thenReturn(Optional.of(machine));
    when(snapshots.findByWorkspaceId(id.value())).thenReturn(List.of());
  }
}
