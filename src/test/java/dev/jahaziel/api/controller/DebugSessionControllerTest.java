/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.debug.api.DebugService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DebugSessionControllerTest {
  private final DebugService debug = mock(DebugService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final DebugSessionController controller =
      new DebugSessionController(debug, authorization);
  private final UserId owner = new UserId(UUID.randomUUID());
  private final WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
  private final UUID sessionId = UUID.randomUUID();
  private final Principal principal = () -> owner.value().toString();

  @Test
  void listsAuthorizedWorkspaceSessionsWithReadPermission() {
    List<DebugService.Session> sessions = List.of(session());
    when(authorization.isAllowed(owner, "read", workspace)).thenReturn(true);
    when(debug.list(workspace)).thenReturn(sessions);

    assertEquals(sessions, controller.list(workspace.value(), principal));
  }

  @Test
  void rejectsDebuggerCreationWithoutWorkspaceWritePermission() {
    when(authorization.isAllowed(owner, "write", workspace)).thenReturn(false);

    assertThrows(
        AccessDeniedException.class,
        () ->
            controller.start(
                workspace.value(),
                new DebugService.StartRequest("node", "src/main.js", null, List.of(), ".", false),
                principal));
    verify(debug, never())
        .start(
            workspace,
            new DebugService.StartRequest("node", "src/main.js", null, List.of(), ".", false));
  }

  @Test
  void stopsOnlyAWorkspaceAuthorizedByWritePermission() {
    when(authorization.isAllowed(owner, "write", workspace)).thenReturn(true);
    when(debug.stop(workspace, sessionId)).thenReturn(session());

    assertEquals(sessionId, controller.stop(workspace.value(), sessionId, principal).id());
    verify(debug).stop(workspace, sessionId);
  }

  private DebugService.Session session() {
    return new DebugService.Session(
        sessionId, workspace.value(), "node", "pwa-node", "RUNNING", "1.0", Instant.now(), "");
  }
}
