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
public abstract class RouteRegistry {
    /**
     * Registers a route.
     *
     * @param userSlug the URL-safe user slug
     * @param port the workspace port
     * @param workspaceId the serving workspace
     */
    public abstract void register(String userSlug, int port, UUID workspaceId);

    /**
     * Removes a route.
     *
     * @param userSlug the URL-safe user slug
     * @param port the workspace port
     */
    public abstract void unregister(String userSlug, int port);

    /**
     * Removes one workspace's route. Implementations that key routes only by user and port can keep
     * the inherited behavior; path-based previews need the precise form.
     *
     * @param userSlug the URL-safe user slug
     * @param port the workspace port
     * @param workspaceId the serving workspace
     */
    public void unregister(String userSlug, int port, UUID workspaceId) {
        unregister(userSlug, port);
    }
}
