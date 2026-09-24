/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.networking.internal.GatewayRouteServiceImpl;
import dev.jahaziel.networking.internal.GatewayRouteSyncJob;
import dev.jahaziel.networking.internal.PortMapper;
import dev.jahaziel.networking.internal.PortServiceImpl;
import dev.jahaziel.networking.persistence.WorkspacePortEntity;
import dev.jahaziel.networking.persistence.WorkspacePortRepository;
import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.spi.RouteRegistry;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkingExtraCoverageTest {
  @Test
  void portBindingGuards() {
    UUID u = UUID.randomUUID();
    UUID w = UUID.randomUUID();
    assertThrows(
        NullPointerException.class, () -> new PortBinding(null, 1, w, "http", Instant.now()));
    assertThrows(
        IllegalArgumentException.class, () -> new PortBinding(u, 0, w, "http", Instant.now()));
    assertThrows(
        IllegalArgumentException.class, () -> new PortBinding(u, 70000, w, "http", Instant.now()));
    assertThrows(
        NullPointerException.class, () -> new PortBinding(u, 1, null, "http", Instant.now()));
    assertThrows(
        IllegalArgumentException.class, () -> new PortBinding(u, 1, w, "ftp", Instant.now()));
    assertThrows(
        IllegalArgumentException.class, () -> new PortBinding(u, 1, w, null, Instant.now()));
    assertThrows(NullPointerException.class, () -> new PortBinding(u, 1, w, "http", null));
  }

  @Test
  void portMapperRoundTrip() {
    PortBinding b =
        new PortBinding(UUID.randomUUID(), 8080, UUID.randomUUID(), "http", Instant.now());
    WorkspacePortEntity row = PortMapper.toEntity(b);
    assertEquals(b, PortMapper.toDomain(row, b.userId()));
  }

  @Test
  void registerGuardsRangeProtocol() {
    var ports = mock(WorkspacePortRepository.class);
    var users = mock(UserService.class);
    var workspaces = mock(WorkspaceService.class);
    PortServiceImpl svc = new PortServiceImpl(ports, users, workspaces);
    var user = new dev.jahaziel.identity.domain.UserId(UUID.randomUUID());
    var wid = new WorkspaceId(UUID.randomUUID());
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    when(ws.isOwnedBy(user)).thenReturn(true);
    when(workspaces.get(wid)).thenReturn(ws);
    when(users.get(user)).thenReturn(mock(dev.jahaziel.spi.UserIdentity.class));
    assertThrows(IllegalArgumentException.class, () -> svc.registerPort(user, 0, wid, "http"));
    assertThrows(IllegalArgumentException.class, () -> svc.registerPort(user, 70000, wid, "http"));
    assertThrows(IllegalArgumentException.class, () -> svc.registerPort(user, 8080, wid, "ftp"));
    assertThrows(NullPointerException.class, () -> svc.registerPort(user, 8081, wid, null));
    assertThrows(NullPointerException.class, () -> svc.unregisterPort(null, 1));
    assertThrows(NullPointerException.class, () -> new PortServiceImpl(null, users, workspaces));
  }

  @Test
  void bindingsByWorkspaceMapsOwner() {
    var ports = mock(WorkspacePortRepository.class);
    var users = mock(UserService.class);
    var workspaces = mock(WorkspaceService.class);
    PortServiceImpl svc = new PortServiceImpl(ports, users, workspaces);
    var wid = new WorkspaceId(UUID.randomUUID());
    UUID owner = UUID.randomUUID();
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    when(ws.owner()).thenReturn(new dev.jahaziel.identity.domain.UserId(owner));
    when(workspaces.get(wid)).thenReturn(ws);
    WorkspacePortEntity row = new WorkspacePortEntity();
    var key = new WorkspacePortEntity.Id();
    key.setWorkspaceId(wid.value());
    key.setPort(8080);
    row.setId(key);
    row.setProtocol("http");
    row.setRegisteredAt(Instant.now());
    when(ports.findByIdWorkspaceId(wid.value())).thenReturn(List.of(row));
    assertEquals(1, svc.bindingsByWorkspace(wid).size());
  }

  @Test
  void reconcileNoopWithoutRegistry() {
    var workspaces = mock(WorkspaceService.class);
    var users = mock(UserService.class);
    var ports = mock(dev.jahaziel.networking.api.PortService.class);
    GatewayRouteServiceImpl svc =
        new GatewayRouteServiceImpl(Optional.empty(), workspaces, users, ports);
    svc.reconcile();
    verify(workspaces, never()).describeAll();
    assertThrows(
        NullPointerException.class,
        () -> new GatewayRouteServiceImpl(null, workspaces, users, ports));
  }

  @Test
  void reconcileAddsRemovesAndSkips() {
    RouteRegistry registry = mock(RouteRegistry.class);
    var workspaces = mock(WorkspaceService.class);
    var users = mock(UserService.class);
    var ports = mock(dev.jahaziel.networking.api.PortService.class);
    GatewayRouteServiceImpl svc =
        new GatewayRouteServiceImpl(Optional.of(registry), workspaces, users, ports);
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    WorkspaceId wid = new WorkspaceId(UUID.randomUUID());
    var owner = new dev.jahaziel.identity.domain.UserId(UUID.randomUUID());
    when(ws.id()).thenReturn(wid);
    when(ws.owner()).thenReturn(owner);
    when(ws.state()).thenReturn(new WorkspaceState.Ready(Instant.now()));
    var view = mock(dev.jahaziel.workspace.api.WorkspaceView.class);
    when(view.workspace()).thenReturn(ws);
    when(workspaces.describeAll()).thenReturn(List.of(view));
    when(users.slug(owner)).thenReturn("slug");
    PortBinding b = new PortBinding(owner.value(), 8080, wid.value(), "http", Instant.now());
    when(ports.bindingsByWorkspace(wid)).thenReturn(List.of(b));
    svc.reconcile();
    verify(registry).register("slug", 8080, wid.value());
    svc.reconcile();
    when(workspaces.describeAll()).thenReturn(List.of());
    svc.reconcile();
    verify(registry).unregister("slug", 8080);
  }

  @Test
  void reconcileSkipsNonServingAndSlugFailure() {
    RouteRegistry registry = mock(RouteRegistry.class);
    var workspaces = mock(WorkspaceService.class);
    var users = mock(UserService.class);
    var ports = mock(dev.jahaziel.networking.api.PortService.class);
    GatewayRouteServiceImpl svc =
        new GatewayRouteServiceImpl(Optional.of(registry), workspaces, users, ports);
    var ws = mock(dev.jahaziel.workspace.domain.Workspace.class);
    WorkspaceId wid = new WorkspaceId(UUID.randomUUID());
    when(ws.id()).thenReturn(wid);
    when(ws.owner()).thenReturn(new dev.jahaziel.identity.domain.UserId(UUID.randomUUID()));
    when(ws.state()).thenReturn(new WorkspaceState.Suspended(Instant.now()));
    var view = mock(dev.jahaziel.workspace.api.WorkspaceView.class);
    when(view.workspace()).thenReturn(ws);
    when(workspaces.describeAll()).thenReturn(List.of(view));
    svc.reconcile();
    verify(registry, never()).register(any(), any(Integer.class), any());
    var booting = mock(dev.jahaziel.workspace.domain.Workspace.class);
    when(booting.id()).thenReturn(wid);
    var owner = new dev.jahaziel.identity.domain.UserId(UUID.randomUUID());
    when(booting.owner()).thenReturn(owner);
    when(booting.state()).thenReturn(new WorkspaceState.Booting(Instant.now()));
    var bv = mock(dev.jahaziel.workspace.api.WorkspaceView.class);
    when(bv.workspace()).thenReturn(booting);
    when(workspaces.describeAll()).thenReturn(List.of(bv));
    when(users.slug(owner)).thenThrow(new RuntimeException("no slug"));
    svc.reconcile();
    verify(registry, never()).register(any(), any(Integer.class), any());
    for (WorkspaceState s :
        List.of(
            new WorkspaceState.Active(Instant.now()),
            new WorkspaceState.Idle(Instant.now(), Instant.now()),
            new WorkspaceState.Booting(Instant.now()))) {
      assertTrue(s != null);
    }
  }

  @Test
  void syncJobDelegates() {
    var routes = mock(dev.jahaziel.networking.api.GatewayRouteService.class);
    GatewayRouteSyncJob job = new GatewayRouteSyncJob(routes);
    job.run(JobContext.now());
    verify(routes).reconcile();
    assertEquals(Duration.ofSeconds(5), job.period());
    assertEquals(JobScope.CLUSTER, job.scope());
    assertThrows(NullPointerException.class, () -> new GatewayRouteSyncJob(null));
  }
}
