/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

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

/**
 * Maps workspace rows to domain objects and back. Pure static functions; the mapping lives at the
 * boundary, not in the domain.
 */
public final class WorkspaceMapper {
  private WorkspaceMapper() {}

  /**
   * Maps a workspace to its row.
   *
   * @param workspace the workspace
   * @param homePath the home folder path on the host
   * @param jailPath the jail path on the host
   * @return the row
   */
  public static WorkspaceEntity toEntity(Workspace workspace, String homePath, String jailPath) {
    WorkspaceEntity row = new WorkspaceEntity();
    row.setWorkspaceId(workspace.id().value());
    row.setOwnerUserId(workspace.owner().value());
    row.setName(workspace.name());
    row.setState(stateToString(workspace.state()));
    row.setStateChangedAt(Instant.now());
    row.setLogicalQuotaBytes(workspace.quota().logicalBytes());
    row.setInodeQuota(workspace.quota().inodeLimit());
    row.setCreatedAt(Instant.now());
    return row;
  }

  /**
   * Maps a machine to its row.
   *
   * @param machine the machine
   * @param workspaceId the workspace
   * @param nodeId the hosting node
   * @param homePath the home folder path on the host
   * @param jailPath the jail path on the host
   * @return the row
   */
  public static VirtualMachineEntity toMachineEntity(
      VirtualMachine machine,
      WorkspaceId workspaceId,
      String nodeId,
      String homePath,
      String jailPath) {
    VirtualMachineEntity row = new VirtualMachineEntity();
    row.setVmId(machine.id().value());
    row.setWorkspaceId(workspaceId.value());
    row.setNodeId(nodeId);
    row.setState("stopped");
    row.setVcpuCount(machine.config().vcpuCount());
    row.setMemoryMib(machine.config().memoryMib());
    row.setHomePath(homePath);
    row.setJailPath(jailPath);
    row.setCreatedAt(Instant.now());
    return row;
  }

  /**
   * Rebuilds a workspace from its rows. Failure reasons are not stored, so restored failures record
   * that the reason was not recorded.
   *
   * @param row the workspace row
   * @param aclRows the grant rows
   * @param machineRow the machine row
   * @param snapshotIds the snapshot identifiers
   * @return the workspace
   */
  public static Workspace toDomain(
      WorkspaceEntity row,
      List<WorkspaceAclEntity> aclRows,
      VirtualMachineEntity machineRow,
      List<SnapshotId> snapshotIds) {
    VirtualMachine machine =
        new VirtualMachine(
            new VirtualMachineId(machineRow.getVmId()),
            new WorkspaceId(row.getWorkspaceId()),
            new VirtualMachineConfiguration(machineRow.getVcpuCount(), machineRow.getMemoryMib()));
    Workspace workspace =
        new Workspace(
            new WorkspaceId(row.getWorkspaceId()),
            new UserId(row.getOwnerUserId()),
            row.getName(),
            new WorkspaceQuota(row.getLogicalQuotaBytes(), row.getInodeQuota()),
            machine);
    for (WorkspaceAclEntity acl : aclRows) {
      workspace.grant(acl.getId().getGranteeUserId(), acl.getId().getPermission());
    }
    for (SnapshotId snapshotId : snapshotIds) {
      workspace.recordSnapshot(snapshotId);
    }
    replay(workspace, row.getState(), row.getStateChangedAt());
    return workspace;
  }

  /**
   * Maps a state to its stored form.
   *
   * @param state the state
   * @return the lowercase stored form
   */
  public static String stateToString(WorkspaceState state) {
    if (state instanceof WorkspaceState.Requested) {
      return "requested";
    }
    if (state instanceof WorkspaceState.Preparing) {
      return "preparing";
    }
    if (state instanceof WorkspaceState.Booting) {
      return "booting";
    }
    if (state instanceof WorkspaceState.Ready) {
      return "ready";
    }
    if (state instanceof WorkspaceState.Active) {
      return "active";
    }
    if (state instanceof WorkspaceState.Idle) {
      return "idle";
    }
    if (state instanceof WorkspaceState.Suspended) {
      return "suspended";
    }
    if (state instanceof WorkspaceState.Destroyed) {
      return "destroyed";
    }
    if (state instanceof WorkspaceState.Failed) {
      return "failed";
    }
    throw new IllegalArgumentException("Unknown state " + state);
  }

  private static void replay(Workspace workspace, String state, Instant at) {
    switch (state) {
      case "requested" -> {}
      case "preparing" -> workspace.prepare();
      case "booting" -> {
        workspace.prepare();
        workspace.boot();
      }
      case "ready" -> {
        workspace.prepare();
        workspace.boot();
        workspace.markReady();
      }
      case "active" -> start(workspace);
      case "idle" -> {
        start(workspace);
        workspace.markIdle(at);
      }
      case "suspended" -> {
        start(workspace);
        workspace.suspend();
      }
      case "destroyed" -> {
        start(workspace);
        workspace.destroy();
      }
      case "failed" -> {
        start(workspace);
        workspace.fail("not recorded");
      }
      default ->
          throw new IllegalStateException(
              "Workspace " + workspace.id() + " has unknown state " + state);
    }
  }

  private static void start(Workspace workspace) {
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
  }
}
