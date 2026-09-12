/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.routing;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory user-port to workspace routing table. */
public final class WorkspaceDirectory {
  private final ConcurrentMap<Route, UUID> routes = new ConcurrentHashMap<>();

  /**
   * Registers a route.
   *
   * @param userSlug the URL-safe user slug
   * @param port the workspace port
   * @param workspaceId the serving workspace
   */
  public void register(String userSlug, int port, UUID workspaceId) {
    routes.put(new Route(userSlug, port), Objects.requireNonNull(workspaceId, "workspaceId"));
  }

  /**
   * Removes a route.
   *
   * @param userSlug the URL-safe user slug
   * @param port the workspace port
   */
  public void unregister(String userSlug, int port) {
    routes.remove(new Route(userSlug, port));
  }

  /**
   * Looks up the workspace serving a route.
   *
   * @param userSlug the URL-safe user slug
   * @param port the workspace port
   * @return the workspace, empty when unregistered
   */
  public Optional<UUID> lookup(String userSlug, int port) {
    return Optional.ofNullable(routes.get(new Route(userSlug, port)));
  }

  private record Route(String userSlug, int port) {
    Route {
      if (userSlug == null || userSlug.isBlank()) {
        throw new IllegalArgumentException("userSlug must not be blank");
      }
    }
  }
}
