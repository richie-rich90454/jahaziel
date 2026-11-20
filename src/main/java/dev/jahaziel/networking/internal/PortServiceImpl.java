/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.networking.persistence.WorkspacePortEntity;
import dev.jahaziel.networking.persistence.WorkspacePortRepository;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Database-backed port bindings. User lookups scan the owner's workspaces. */
public final class PortServiceImpl extends PortService {
    private static final Set<Integer> RESERVED = Set.of(22, 25);
    private static final Set<String> PROTOCOLS = Set.of("http", "https", "tcp");

    private final WorkspacePortRepository ports;
    private final UserService users;
    private final WorkspaceService workspaces;

    /**
     * Creates the service.
     *
     * @param ports the port rows
     * @param users the user lookup
     * @param workspaces the workspace lookup
     */
    public PortServiceImpl(
            WorkspacePortRepository ports, UserService users, WorkspaceService workspaces) {
        this.ports = Objects.requireNonNull(ports, "ports");
        this.users = Objects.requireNonNull(users, "users");
        this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    }

    @Override
    public PortBinding registerPort(
            UserId userId, int port, WorkspaceId workspaceId, String protocol) {
        users.get(userId);
        Workspace workspace = workspaces.get(workspaceId);
        if (!workspace.isOwnedBy(userId)) {
            throw new IllegalArgumentException("workspace is not owned by the user");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        if (!PROTOCOLS.contains(protocol)) {
            throw new IllegalArgumentException("unknown protocol: " + protocol);
        }
        if (RESERVED.contains(port)) {
            throw new IllegalArgumentException("port " + port + " is reserved and never exposed");
        }
        WorkspacePortEntity.Id key = new WorkspacePortEntity.Id();
        key.setWorkspaceId(workspaceId.value());
        key.setPort(port);
        if (ports.existsById(key)) {
            throw new IllegalArgumentException("port " + port + " already registered");
        }
        if (ports.findByIdWorkspaceId(workspaceId.value()).size()
                >= PortService.MAX_PORTS_PER_WORKSPACE) {
            throw new IllegalArgumentException(
                    "a workspace can register at most "
                            + PortService.MAX_PORTS_PER_WORKSPACE
                            + " ports");
        }
        PortBinding binding =
                new PortBinding(userId.value(), port, workspaceId.value(), protocol, Instant.now());
        ports.save(PortMapper.toEntity(binding));
        return binding;
    }

    @Override
    public void unregisterPort(UserId userId, int port) {
        Objects.requireNonNull(userId, "userId");
        WorkspacePortEntity row =
                findUserPort(userId, port)
                        .orElseThrow(
                                new java.util.function.Supplier<IllegalArgumentException>() {
                                    @Override
                                    public IllegalArgumentException get() {
                                        return new IllegalArgumentException(
                                                "port " + port + " not registered");
                                    }
                                });
        ports.delete(row);
    }

    @Override
    public void unregisterWorkspacePorts(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        for (WorkspacePortEntity row : ports.findByIdWorkspaceId(workspaceId.value())) {
            ports.delete(row);
        }
    }

    @Override
    public List<PortBinding> bindingsByWorkspace(WorkspaceId workspaceId) {
        Workspace workspace = workspaces.get(workspaceId);
        UUID owner = workspace.owner().value();
        List<PortBinding> result = new ArrayList<PortBinding>();
        for (WorkspacePortEntity row : ports.findByIdWorkspaceId(workspaceId.value())) {
            result.add(PortMapper.toDomain(row, owner));
        }
        return result;
    }

    @Override
    public Optional<WorkspaceId> lookup(UUID userId, int port) {
        Optional<WorkspacePortEntity> found = findUserPort(new UserId(userId), port);
        if (!found.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new WorkspaceId(found.get().getId().getWorkspaceId()));
    }

    private Optional<WorkspacePortEntity> findUserPort(UserId userId, int port) {
        for (WorkspaceView view : workspaces.describeByOwner(userId)) {
            for (WorkspacePortEntity row :
                    ports.findByIdWorkspaceId(view.workspace().id().value())) {
                if (row.getId().getPort() == port) {
                    return Optional.of(row);
                }
            }
        }
        return Optional.empty();
    }
}
