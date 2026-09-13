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
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Database-backed port bindings. User lookups scan the owner's workspaces. */
public final class PortServiceImpl implements PortService {
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
    var workspace = workspaces.get(workspaceId);
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
            .orElseThrow(() -> new IllegalArgumentException("port " + port + " not registered"));
    ports.delete(row);
  }

  @Override
  public List<PortBinding> bindingsByWorkspace(WorkspaceId workspaceId) {
    var workspace = workspaces.get(workspaceId);
    UUID owner = workspace.owner().value();
    return ports.findByIdWorkspaceId(workspaceId.value()).stream()
        .map(row -> PortMapper.toDomain(row, owner))
        .toList();
  }

  @Override
  public Optional<WorkspaceId> lookup(UUID userId, int port) {
    return findUserPort(new UserId(userId), port)
        .map(row -> new WorkspaceId(row.getId().getWorkspaceId()));
  }

  private Optional<WorkspacePortEntity> findUserPort(UserId userId, int port) {
    return workspaces.describeByOwner(userId).stream()
        .flatMap(view -> ports.findByIdWorkspaceId(view.workspace().id().value()).stream())
        .filter(row -> row.getId().getPort() == port)
        .findFirst();
  }
}
