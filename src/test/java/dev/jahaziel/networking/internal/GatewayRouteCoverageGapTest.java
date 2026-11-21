/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.spi.RouteRegistry;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class GatewayRouteCoverageGapTest {
    @Test
    void reconcileRegistersEveryServingState() {
        RouteRegistry registry = mock(RouteRegistry.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        UserService users = mock(UserService.class);
        PortService ports = mock(PortService.class);
        GatewayRouteServiceImpl svc =
                new GatewayRouteServiceImpl(Optional.of(registry), workspaces, users, ports);
        Instant now = Instant.now();
        List<WorkspaceState> serving =
                List.of(
                        new WorkspaceState.Booting(now),
                        new WorkspaceState.Ready(now),
                        new WorkspaceState.Active(now),
                        new WorkspaceState.Idle(now, now));
        for (WorkspaceState state : serving) {
            WorkspaceId wid = new WorkspaceId(UUID.randomUUID());
            dev.jahaziel.identity.domain.UserId owner =
                    new dev.jahaziel.identity.domain.UserId(UUID.randomUUID());
            Workspace ws = mock(Workspace.class);
            when(ws.id()).thenReturn(wid);
            when(ws.owner()).thenReturn(owner);
            when(ws.state()).thenReturn(state);
            WorkspaceView view = mock(WorkspaceView.class);
            when(view.workspace()).thenReturn(ws);
            when(workspaces.describeAll()).thenReturn(List.of(view));
            when(users.slug(owner)).thenReturn("slug-" + wid.value().toString().substring(0, 8));
            PortBinding binding =
                    new PortBinding(owner.value(), 8080, wid.value(), "http", Instant.now());
            when(ports.bindingsByWorkspace(wid)).thenReturn(List.of(binding));
            svc.reconcile();
        }
        verify(registry, org.mockito.Mockito.times(4))
                .register(anySlug(), org.mockito.ArgumentMatchers.eq(8080), anyId());
    }

    @Test
    void reconcileRegistersBothWorkspacesSharingOneUserPort() {
        RouteRegistry registry = mock(RouteRegistry.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        UserService users = mock(UserService.class);
        PortService ports = mock(PortService.class);
        GatewayRouteServiceImpl svc =
                new GatewayRouteServiceImpl(Optional.of(registry), workspaces, users, ports);
        Instant now = Instant.now();
        List<WorkspaceView> views = new java.util.ArrayList<>();
        List<UUID> ids = new java.util.ArrayList<>();
        for (int index = 0; index < 2; index++) {
            WorkspaceId wid = new WorkspaceId(UUID.randomUUID());
            ids.add(wid.value());
            dev.jahaziel.identity.domain.UserId owner =
                    new dev.jahaziel.identity.domain.UserId(UUID.randomUUID());
            Workspace ws = mock(Workspace.class);
            when(ws.id()).thenReturn(wid);
            when(ws.owner()).thenReturn(owner);
            when(ws.state()).thenReturn(new WorkspaceState.Ready(now));
            WorkspaceView view = mock(WorkspaceView.class);
            when(view.workspace()).thenReturn(ws);
            when(users.slug(owner)).thenReturn("richard");
            PortBinding binding = new PortBinding(owner.value(), 8080, wid.value(), "http", now);
            when(ports.bindingsByWorkspace(wid)).thenReturn(List.of(binding));
            views.add(view);
        }
        when(workspaces.describeAll()).thenReturn(views);

        svc.reconcile();

        verify(registry).register("richard", 8080, ids.get(0));
        verify(registry).register("richard", 8080, ids.get(1));
    }

    private static String anySlug() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    private static UUID anyId() {
        return org.mockito.ArgumentMatchers.any();
    }
}
