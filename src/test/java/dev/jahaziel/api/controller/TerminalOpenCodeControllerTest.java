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

import dev.jahaziel.agenttools.api.AgentToolNotInstalledException;
import dev.jahaziel.agenttools.api.AgentToolService;
import dev.jahaziel.agenttools.api.AgentToolService.ToolStatus;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.domain.TerminalSession;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TerminalOpenCodeControllerTest {
  private final TerminalService terminals = mock(TerminalService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final AgentToolService tools = mock(AgentToolService.class);
  private final UserId user = new UserId(UUID.randomUUID());
  private final WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
  private final Principal principal = () -> user.value().toString();
  private final TerminalController controller =
      new TerminalController(terminals, authorization, tools);

  @BeforeEach
  void allowWorkspaceWrites() {
    when(authorization.isAllowed(user, "write", workspace)).thenReturn(true);
  }

  @Test
  void startsOnlyTheServerManagedOpenCodeCommandInAnAuthorizedWorkspace() {
    when(tools.catalog(user)).thenReturn(List.of(status("INSTALLED")));
    TerminalSession session =
        new TerminalSession(new TerminalSessionId(UUID.randomUUID()), workspace, user, 30, 120);
    when(terminals.open(workspace, user, 30, 120)).thenReturn(session);

    var result = controller.openOpenCode(workspace.value(), 30, 120, principal);

    assertEquals(session.id().value(), result.sessionId());
    verify(terminals).write(session.id(), "opencode\r");
  }

  @Test
  void refusesToLaunchWhenTheUserHasNotInstalledOpenCode() {
    when(tools.catalog(user)).thenReturn(List.of(status("NOT_INSTALLED")));

    assertThrows(
        AgentToolNotInstalledException.class,
        () -> controller.openOpenCode(workspace.value(), 30, 120, principal));

    verify(terminals, never()).open(workspace, user, 30, 120);
  }

  @Test
  void checksWorkspaceAuthorizationBeforeReadingTheUserToolCatalog() {
    when(authorization.isAllowed(user, "write", workspace)).thenReturn(false);

    assertThrows(
        AccessDeniedException.class,
        () -> controller.openOpenCode(workspace.value(), 30, 120, principal));

    verify(tools, never()).catalog(user);
    verify(terminals, never()).open(workspace, user, 30, 120);
  }

  private static ToolStatus status(String status) {
    return new ToolStatus("opencode", "OpenCode", "1.19.5", "x64", status, 10, 20, 0, "");
  }
}
