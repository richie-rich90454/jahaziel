/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.routing;

import dev.jahaziel.spi.RouteRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory user-port to workspace routing table. */
public final class WorkspaceDirectory extends RouteRegistry {
    private final ConcurrentMap<Route, UUID> routes = new ConcurrentHashMap<>();

    /**
     * Registers a route.
     *
     * @param userSlug the URL-safe user slug
     * @param port the workspace port
     * @param workspaceId the serving workspace
     */
    @Override
    public void register(String userSlug, int port, UUID workspaceId) {
        routes.put(new Route(userSlug, port), Objects.requireNonNull(workspaceId, "workspaceId"));
    }

    /**
     * Removes a route.
     *
     * @param userSlug the URL-safe user slug
     * @param port the workspace port
     */
    @Override
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

    /**
     * Returns the registered user slug for a workspace port. The path-based preview gateway uses
     * the workspace id in the public URL, then maps it to the same host-form route used by the
     * relay.
     *
     * @param workspaceId the workspace expected to serve the port
     * @param port the registered port
     * @return the user slug when this workspace and port are currently routable
     */
    public Optional<String> lookupSlug(UUID workspaceId, int port) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (port < 1 || port > 65535) {
            return Optional.empty();
        }
        for (Map.Entry<Route, UUID> entry : routes.entrySet()) {
            if (entry.getKey().port() == port && entry.getValue().equals(workspaceId)) {
                return Optional.of(entry.getKey().userSlug());
            }
        }
        return Optional.empty();
    }

    /** A user-slug and workspace-port route key. */
    private static final class Route {
        private final String userSlug;
        private final int port;

        Route(String userSlug, int port) {
            if (userSlug == null || userSlug.isBlank()) {
                throw new IllegalArgumentException("userSlug must not be blank");
            }
            this.userSlug = userSlug;
            this.port = port;
        }

        String userSlug() {
            return userSlug;
        }

        int port() {
            return port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Route)) return false;
            Route other = (Route) o;
            return port == other.port && userSlug.equals(other.userSlug);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userSlug, port);
        }
    }
}
