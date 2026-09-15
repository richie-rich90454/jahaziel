/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.UUID;

/**
 * Where the gateway learns which workspace serves a user host and port. The control plane owns the
 * truth; the gateway consults this table on every routed request.
 */
public interface RouteRegistry {
  /**
   * Registers a route.
   *
   * @param userSlug the URL-safe user slug
   * @param port the workspace port
   * @param workspaceId the serving workspace
   */
  void register(String userSlug, int port, UUID workspaceId);

  /**
   * Removes a route.
   *
   * @param userSlug the URL-safe user slug
   * @param port the workspace port
   */
  void unregister(String userSlug, int port);
}
