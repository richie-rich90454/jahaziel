/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.networking.api.GatewayRouteService;
import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.spi.RouteRegistry;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reconciles the gateway routing table from the database. A workspace is routable while it is
 * serving (ready, active, or idle) and has registered ports; everything else is removed. Without a
 * registry, as in the split topology before route push lands, this is a no-op.
 */
public final class GatewayRouteServiceImpl implements GatewayRouteService {
  private final Optional<RouteRegistry> registry;
  private final WorkspaceService workspaces;
  private final UserService users;
  private final PortService ports;
  private final ConcurrentMap<RouteKey, UUID> registered = new ConcurrentHashMap<>();

  /**
   * Creates the service.
   *
   * @param registry the gateway registry, present in portable single-process mode
   * @param workspaces the workspace lookup
   * @param users the user lookup, for slugs
   * @param ports the port lookup
   */
  public GatewayRouteServiceImpl(
      Optional<RouteRegistry> registry,
      WorkspaceService workspaces,
      UserService users,
      PortService ports) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.users = Objects.requireNonNull(users, "users");
    this.ports = Objects.requireNonNull(ports, "ports");
  }

  @Override
  public synchronized void reconcile() {
    if (registry.isEmpty()) {
      return;
    }
    Map<RouteKey, UUID> desired = desiredRoutes();
    for (Map.Entry<RouteKey, UUID> current : new ArrayList<>(registered.entrySet())) {
      if (!current.getValue().equals(desired.get(current.getKey()))) {
        RouteKey key = current.getKey();
        registry.get().unregister(key.slug(), key.port());
        registered.remove(key);
      }
    }
    for (Map.Entry<RouteKey, UUID> wanted : desired.entrySet()) {
      if (!wanted.getValue().equals(registered.get(wanted.getKey()))) {
        RouteKey key = wanted.getKey();
        registry.get().register(key.slug(), key.port(), wanted.getValue());
        registered.put(key, wanted.getValue());
      }
    }
  }

  private Map<RouteKey, UUID> desiredRoutes() {
    Map<RouteKey, UUID> desired = new LinkedHashMap<>();
    for (WorkspaceView view : workspaces.describeAll()) {
      if (!serving(view.workspace().state())) {
        continue;
      }
      String slug;
      try {
        slug = users.slug(view.workspace().owner());
      } catch (RuntimeException noSlug) {
        continue;
      }
      WorkspaceId workspaceId = view.workspace().id();
      for (PortBinding binding : ports.bindingsByWorkspace(workspaceId)) {
        desired.put(new RouteKey(slug, binding.port()), workspaceId.value());
      }
    }
    return desired;
  }

  private static boolean serving(WorkspaceState state) {
    // Booting counts: on the zero-install local backend nothing calls markReady, and the workspace
    // is already up. The gateway answers 502 until the workspace's port actually listens.
    return state instanceof WorkspaceState.Booting
        || state instanceof WorkspaceState.Ready
        || state instanceof WorkspaceState.Active
        || state instanceof WorkspaceState.Idle;
  }

  private record RouteKey(String slug, int port) {
    RouteKey {
      Objects.requireNonNull(slug, "slug");
    }
  }
}
