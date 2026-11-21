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
    private final ConcurrentMap<WorkspacePort, String> pathRoutes = new ConcurrentHashMap<>();

    /**
     * Registers a route.
     *
     * @param userSlug the URL-safe user slug
     * @param port the workspace port
     * @param workspaceId the serving workspace
     */
    @Override
    public void register(String userSlug, int port, UUID workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        routes.put(new Route(userSlug, port), workspaceId);
        pathRoutes.put(new WorkspacePort(workspaceId, port), userSlug);
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
        for (Map.Entry<WorkspacePort, String> entry : pathRoutes.entrySet()) {
            if (entry.getKey().port() == port && entry.getValue().equals(userSlug)) {
                pathRoutes.remove(entry.getKey());
            }
        }
    }

    @Override
    public void unregister(String userSlug, int port, UUID workspaceId) {
        pathRoutes.remove(new WorkspacePort(workspaceId, port));
        Route route = new Route(userSlug, port);
        if (!workspaceId.equals(routes.get(route))) {
            return;
        }
        routes.remove(route);
        String replacement = null;
        for (Map.Entry<WorkspacePort, String> entry : pathRoutes.entrySet()) {
            if (entry.getKey().port() != port || !entry.getValue().equals(userSlug)) {
                continue;
            }
            String candidate = entry.getKey().workspaceId().toString();
            if (replacement == null || candidate.compareTo(replacement) < 0) {
                replacement = candidate;
            }
        }
        if (replacement != null) {
            routes.put(route, UUID.fromString(replacement));
        }
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
        return Optional.ofNullable(pathRoutes.get(new WorkspacePort(workspaceId, port)));
    }

    /** A workspace-port key for path-based previews, which are workspace-scoped. */
    private static final class WorkspacePort {
        private final UUID workspaceId;
        private final int port;

        WorkspacePort(UUID workspaceId, int port) {
            this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
            this.port = port;
        }

        UUID workspaceId() {
            return workspaceId;
        }

        int port() {
            return port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WorkspacePort)) return false;
            WorkspacePort other = (WorkspacePort) o;
            return port == other.port && workspaceId.equals(other.workspaceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(workspaceId, port);
        }
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
