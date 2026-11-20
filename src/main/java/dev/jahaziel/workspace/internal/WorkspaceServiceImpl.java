/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.workspace.api.WorkspaceNotFoundException;
import dev.jahaziel.workspace.api.WorkspaceProcessLifecycle;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import dev.jahaziel.workspace.persistence.VirtualMachineEntity;
import dev.jahaziel.workspace.persistence.VirtualMachineRepository;
import dev.jahaziel.workspace.persistence.WorkspaceAclEntity;
import dev.jahaziel.workspace.persistence.WorkspaceAclRepository;
import dev.jahaziel.workspace.persistence.WorkspaceEntity;
import dev.jahaziel.workspace.persistence.WorkspaceRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Database-backed workspaces. Paths follow the node-agent jail layout deterministically. */
public final class WorkspaceServiceImpl extends WorkspaceService {
    private static final StorageUsage UNKNOWN_USAGE = new StorageUsage(0, 0, 0, 0);

    private final WorkspaceRepository workspaces;
    private final WorkspaceAclRepository acl;
    private final VirtualMachineRepository machines;
    private final UserService users;
    private final String storageRoot;
    private final WorkspaceProcessLifecycle processes;

    /**
     * Creates the service.
     *
     * @param workspaces the workspace rows
     * @param acl the grant rows
     * @param machines the machine rows
     * @param users the user lookup
     * @param storageRoot the storage root, for example {@code /var/lib/jahaziel}
     */
    public WorkspaceServiceImpl(
            WorkspaceRepository workspaces,
            WorkspaceAclRepository acl,
            VirtualMachineRepository machines,
            UserService users,
            String storageRoot) {
        this(workspaces, acl, machines, users, storageRoot, null);
    }

    /** Creates the service with lifecycle cleanup for interactive processes. */
    public WorkspaceServiceImpl(
            WorkspaceRepository workspaces,
            WorkspaceAclRepository acl,
            VirtualMachineRepository machines,
            UserService users,
            String storageRoot,
            WorkspaceProcessLifecycle processes) {
        this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
        this.acl = Objects.requireNonNull(acl, "acl");
        this.machines = Objects.requireNonNull(machines, "machines");
        this.users = Objects.requireNonNull(users, "users");
        if (storageRoot == null || storageRoot.isBlank()) {
            throw new IllegalArgumentException("storageRoot must not be blank");
        }
        this.storageRoot = storageRoot;
        this.processes = processes;
    }

    @Override
    public Workspace create(
            UserId owner, String name, int vcpuCount, long memoryMib, NodeId nodeId) {
        users.get(owner);
        checkName(name);
        if (vcpuCount < 1) {
            throw new IllegalArgumentException("vcpuCount must be positive");
        }
        if (memoryMib < 1) {
            throw new IllegalArgumentException("memoryMib must be positive");
        }
        Objects.requireNonNull(nodeId, "nodeId");
        for (WorkspaceEntity candidate : workspaces.findByOwnerUserId(owner.value())) {
            if (candidate.getName().equals(name)) {
                throw new IllegalArgumentException("workspace name already exists: " + name);
            }
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
        String home = WorkspacePaths.homePath(storageRoot, workspaceId);
        workspaces.save(WorkspaceMapper.toEntity(workspace, home, jail));
        try {
            machines.save(
                    WorkspaceMapper.toMachineEntity(
                            machine, workspaceId, nodeId.value(), home, jail));
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
        List<WorkspaceView> result = new ArrayList<>();
        for (WorkspaceEntity row : workspaces.findByOwnerUserId(owner.value())) {
            result.add(viewOf(row));
        }
        return result;
    }

    @Override
    public List<WorkspaceView> describeAll() {
        List<WorkspaceView> result = new ArrayList<>();
        for (WorkspaceEntity row : workspaces.findAll()) {
            result.add(viewOf(row));
        }
        return result;
    }

    @Override
    public void save(Workspace workspace) {
        Objects.requireNonNull(workspace, "workspace");
        WorkspaceEntity row = rowOf(workspace.id());
        String state = WorkspaceMapper.stateToString(workspace.state());
        // The timestamp means "entered the current state at". Bumping it on every save turned
        // the UI's state-age display into "time since last write", which jumps on every
        // unrelated write and lies after quiet stretches.
        if (!state.equals(row.getState())) {
            row.setState(state);
            row.setStateChangedAt(Instant.now());
        }
        row.setStateReason(WorkspaceMapper.stateReasonOf(workspace.state()));
        row.setLogicalQuotaBytes(workspace.quota().logicalBytes());
        row.setInodeQuota(workspace.quota().inodeLimit());
        workspaces.save(row);
    }

    @Override
    public void delete(WorkspaceId id) {
        if (processes != null) {
            processes.deleteWorkspace(id);
        }
        Workspace workspace = load(id);
        workspace.destroy();
        workspaces.deleteById(id.value());
    }

    private Workspace load(WorkspaceId id) {
        return assemble(rowOf(id));
    }

    private WorkspaceEntity rowOf(WorkspaceId id) {
        Optional<WorkspaceEntity> found = workspaces.findById(id.value());
        if (!found.isPresent()) {
            throw new WorkspaceNotFoundException(id);
        }
        return found.get();
    }

    private WorkspaceView viewOf(WorkspaceEntity row) {
        Workspace workspace = assemble(row);
        return new WorkspaceView(
                workspace, row.getCreatedAt(), row.getStateChangedAt(), UNKNOWN_USAGE);
    }

    private Workspace assemble(WorkspaceEntity row) {
        WorkspaceId id = new WorkspaceId(row.getWorkspaceId());
        List<WorkspaceAclEntity> grants = acl.findByIdWorkspaceId(id.value());
        Optional<VirtualMachineEntity> machineRow = machines.findByWorkspaceId(id.value());
        if (!machineRow.isPresent()) {
            throw new IllegalStateException("Workspace " + id + " has no machine row");
        }
        return WorkspaceMapper.toDomain(row, grants, machineRow.get(), List.of());
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
