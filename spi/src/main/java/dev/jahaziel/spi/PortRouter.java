/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.Optional;
import java.util.UUID;

/** Maps user ports to workspaces for the gateway. */
@JahazielSpi(version = 1)
public interface PortRouter {
  /**
   * Registers a port binding.
   *
   * @param userId the owning user
   * @param port the bound port
   * @param workspaceId the workspace serving the port
   */
  void register(UUID userId, int port, UUID workspaceId);

  /**
   * Removes a port binding.
   *
   * @param userId the owning user
   * @param port the bound port
   */
  void unregister(UUID userId, int port);

  /**
   * Looks up the workspace serving a user port.
   *
   * @param userId the owning user
   * @param port the bound port
   * @return the workspace, empty when unregistered
   */
  Optional<UUID> lookup(UUID userId, int port);
}
