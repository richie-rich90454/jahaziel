/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.api;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port bindings for the gateway. Bind events are auditable. */
public interface PortService {
  /**
   * Registers a port binding. Reserved ports 22 and 25 are never exposed.
   *
   * @param userId the owning user
   * @param port the bound port
   * @param workspaceId the serving workspace
   * @param protocol the transport protocol
   * @return the binding
   */
  PortBinding registerPort(UserId userId, int port, WorkspaceId workspaceId, String protocol);

  /**
   * Removes a port binding.
   *
   * @param userId the owning user
   * @param port the bound port
   */
  void unregisterPort(UserId userId, int port);

  /**
   * Removes every binding of a workspace, for deletion. Never throws for a workspace without
   * bindings.
   *
   * @param workspaceId the workspace
   */
  void unregisterWorkspacePorts(WorkspaceId workspaceId);

  /**
   * Lists a workspace's bindings.
   *
   * @param workspaceId the workspace
   * @return the bindings
   */
  List<PortBinding> bindingsByWorkspace(WorkspaceId workspaceId);

  /**
   * Looks up the workspace serving a user port.
   *
   * @param userId the owning user
   * @param port the bound port
   * @return the workspace, empty when unregistered
   */
  Optional<WorkspaceId> lookup(UUID userId, int port);
}
