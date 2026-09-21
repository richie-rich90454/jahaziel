/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.runtime.domain.VirtualMachine;
import dev.jahaziel.runtime.domain.VirtualMachineConfiguration;
import dev.jahaziel.runtime.domain.VirtualMachineId;
import dev.jahaziel.runtime.persistence.VirtualMachineEntity;
import dev.jahaziel.runtime.persistence.VirtualMachineRepository;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.persistence.SnapshotRepository;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.workspace.api.WorkspaceNotFoundException;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import dev.jahaziel.workspace.persistence.WorkspaceAclEntity;
import dev.jahaziel.workspace.persistence.WorkspaceAclRepository;
import dev.jahaziel.workspace.persistence.WorkspaceEntity;
import dev.jahaziel.workspace.persistence.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Database-backed workspaces. Paths follow the node-agent jail layout deterministically. */
public final class WorkspaceServiceImpl implements WorkspaceService {
  private static final StorageUsage UNKNOWN_USAGE = new StorageUsage(0, 0, 0, 0);

  private final WorkspaceRepository workspaces;
  private final WorkspaceAclRepository acl;
  private final VirtualMachineRepository machines;
  private final SnapshotRepository snapshots;
  private final UserService users;
  private final String storageRoot;

  /**
   * Creates the service.
   *
   * @param workspaces the workspace rows
   * @param acl the grant rows
   * @param machines the machine rows
   * @param snapshots the snapshot rows
   * @param users the user lookup
   * @param storageRoot the storage root, for example {@code /var/lib/jahaziel}
   */
  public WorkspaceServiceImpl(
      WorkspaceRepository workspaces,
      WorkspaceAclRepository acl,
      VirtualMachineRepository machines,
      SnapshotRepository snapshots,
      UserService users,
      String storageRoot) {
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.acl = Objects.requireNonNull(acl, "acl");
    this.machines = Objects.requireNonNull(machines, "machines");
    this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    this.users = Objects.requireNonNull(users, "users");
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    this.storageRoot = storageRoot;
  }

  @Override
  public Workspace create(UserId owner, String name, int vcpuCount, long memoryMib, NodeId nodeId) {
    users.get(owner);
    checkName(name);
    if (vcpuCount < 1) {
      throw new IllegalArgumentException("vcpuCount must be positive");
    }
    if (memoryMib < 1) {
      throw new IllegalArgumentException("memoryMib must be positive");
    }
    Objects.requireNonNull(nodeId, "nodeId");
    boolean nameTaken =
        workspaces.findByOwnerUserId(owner.value()).stream()
            .anyMatch(row -> row.getName().equals(name));
    if (nameTaken) {
      throw new IllegalArgumentException("workspace name already exists: " + name);
    }
    WorkspaceId workspaceId = new WorkspaceId(UUID.randomUUID());
    VirtualMachine machine =
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()),
            workspaceId,
            new VirtualMachineConfiguration(vcpuCount, memoryMib));
    Workspace workspace =
        new Workspace(workspaceId, owner, name, WorkspaceQuota.defaults(), machine);
    String jail = WorkspacePaths.jailPath(storageRoot, workspaceId);
    String home = WorkspacePaths.homePath(storageRoot, owner);
    workspaces.save(WorkspaceMapper.toEntity(workspace, home, jail));
    try {
      machines.save(
          WorkspaceMapper.toMachineEntity(machine, workspaceId, nodeId.value(), home, jail));
    } catch (RuntimeException failure) {
      workspaces.deleteById(workspaceId.value());
      throw failure;
    }
    return workspace;
  }

  @Override
  public Workspace get(WorkspaceId id) {
    return assemble(rowOf(id));
  }

  @Override
  public WorkspaceView describe(WorkspaceId id) {
    return viewOf(rowOf(id));
  }

  @Override
  public List<WorkspaceView> describeByOwner(UserId owner) {
    return workspaces.findByOwnerUserId(owner.value()).stream().map(this::viewOf).toList();
  }

  @Override
  public List<WorkspaceView> describeAll() {
    return workspaces.findAll().stream().map(this::viewOf).toList();
  }

  @Override
  public void save(Workspace workspace) {
    Objects.requireNonNull(workspace, "workspace");
    WorkspaceEntity row = rowOf(workspace.id());
    row.setState(WorkspaceMapper.stateToString(workspace.state()));
    row.setStateChangedAt(Instant.now());
    row.setLogicalQuotaBytes(workspace.quota().logicalBytes());
    row.setInodeQuota(workspace.quota().inodeLimit());
    workspaces.save(row);
  }

  @Override
  public void delete(WorkspaceId id) {
    Workspace workspace = load(id);
    workspace.destroy();
    workspaces.deleteById(id.value());
  }

  private Workspace load(WorkspaceId id) {
    return assemble(rowOf(id));
  }

  private WorkspaceEntity rowOf(WorkspaceId id) {
    return workspaces.findById(id.value()).orElseThrow(() -> new WorkspaceNotFoundException(id));
  }

  private WorkspaceView viewOf(WorkspaceEntity row) {
    Workspace workspace = assemble(row);
    return new WorkspaceView(workspace, row.getCreatedAt(), row.getStateChangedAt(), UNKNOWN_USAGE);
  }

  private Workspace assemble(WorkspaceEntity row) {
    WorkspaceId id = new WorkspaceId(row.getWorkspaceId());
    List<WorkspaceAclEntity> grants = acl.findByIdWorkspaceId(id.value());
    VirtualMachineEntity machineRow =
        machines
            .findByWorkspaceId(id.value())
            .orElseThrow(
                () -> new IllegalStateException("Workspace " + id + " has no machine row"));
    List<SnapshotId> snapshotIds =
        snapshots.findByWorkspaceId(id.value()).stream()
            .map(snapshot -> new SnapshotId(snapshot.getSnapshotId()))
            .toList();
    return WorkspaceMapper.toDomain(row, grants, machineRow, snapshotIds);
  }

  private static void checkName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (name.length() > 64) {
      throw new IllegalArgumentException("name must be at most 64 characters");
    }
  }
}
