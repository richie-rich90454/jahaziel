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
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.workspace.api.WorkspaceService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
            900);
    SnapshotController controller = new SnapshotController(snapshots, workspaces, auth, dtos);
    UUID id = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> controller.list(id, () -> "nope"));
    assertThrows(
        IllegalArgumentException.class,
        () -> controller.create(id, new CreateSnapshotRequest("t"), () -> "bad"));
    assertThrows(
        IllegalArgumentException.class,
        () -> controller.restore(id, UUID.randomUUID(), () -> "nope"));
  }

  @Test
  void terminalBadPrincipal() {
    TerminalService terminals = mock(TerminalService.class);
    AuthorizationService auth = mock(AuthorizationService.class);
    TerminalController controller = new TerminalController(terminals, auth);
    UUID id = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> controller.open(id, 24, 80, () -> "nope"));
    assertThrows(IllegalArgumentException.class, () -> controller.list(id, () -> "bad"));
    var session = mock(dev.jahaziel.terminal.domain.TerminalSession.class);
    var workspaceId = new dev.jahaziel.workspace.domain.WorkspaceId(UUID.randomUUID());
    when(session.workspaceId()).thenReturn(workspaceId);
    when(terminals.get(any())).thenReturn(session);
    assertThrows(
        IllegalArgumentException.class, () -> controller.close(UUID.randomUUID(), () -> "nope"));
  }
}
