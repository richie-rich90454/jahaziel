/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.api.WorkspaceDtoFactory;
import dev.jahaziel.api.dto.CreateSnapshotRequest;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.networking.api.GatewayEndpoint;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.runtime.internal.BootProgressService;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.workspace.api.WorkspaceService;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class ControllerCoverageGapTest {

    @Test
    void snapshotBadPrincipal() {
        SnapshotService snapshots = mock(SnapshotService.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        AuthorizationService auth = mock(AuthorizationService.class);
        PortService ports = mock(PortService.class);
        when(ports.bindingsByWorkspace(any())).thenReturn(List.of());
        WorkspaceDtoFactory dtos =
                new WorkspaceDtoFactory(
                        ports,
                        mock(UserService.class),
                        mock(JwtService.class),
                        new GatewayEndpoint("https", "code.test", 443),
                        new BootProgressService(),
                        900);
        SnapshotController controller = new SnapshotController(snapshots, workspaces, auth, dtos);
        UUID id = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        controller.list(
                                id,
                                new java.security.Principal() {

                                    public String getName() {
                                        return "nope";
                                    }
                                });
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        controller.create(
                                id,
                                new CreateSnapshotRequest("t"),
                                new java.security.Principal() {

                                    public String getName() {
                                        return "bad";
                                    }
                                });
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        controller.restore(
                                id,
                                UUID.randomUUID(),
                                new java.security.Principal() {

                                    public String getName() {
                                        return "nope";
                                    }
                                });
                    }
                });
    }

    @Test
    void terminalBadPrincipal() {
        TerminalService terminals = mock(TerminalService.class);
        AuthorizationService auth = mock(AuthorizationService.class);
        TerminalController controller = new TerminalController(terminals, auth);
        UUID id = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        controller.open(
                                id,
                                24,
                                80,
                                new java.security.Principal() {

                                    public String getName() {
                                        return "nope";
                                    }
                                });
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        controller.list(
                                id,
                                new java.security.Principal() {

                                    public String getName() {
                                        return "bad";
                                    }
                                });
                    }
                });
        dev.jahaziel.terminal.domain.TerminalSession session =
                mock(dev.jahaziel.terminal.domain.TerminalSession.class);
        dev.jahaziel.workspace.domain.WorkspaceId workspaceId =
                new dev.jahaziel.workspace.domain.WorkspaceId(UUID.randomUUID());
        when(session.workspaceId()).thenReturn(workspaceId);
        when(terminals.get(any())).thenReturn(session);
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        controller.close(
                                UUID.randomUUID(),
                                new java.security.Principal() {

                                    public String getName() {
                                        return "nope";
                                    }
                                });
                    }
                });
    }
}
