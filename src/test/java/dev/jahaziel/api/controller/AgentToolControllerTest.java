/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.agenttools.api.AgentToolService;
import dev.jahaziel.agenttools.api.AgentToolService.ToolStatus;
import dev.jahaziel.identity.domain.UserId;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentToolControllerTest {
  private final AgentToolService tools = mock(AgentToolService.class);
  private final AgentToolController controller = new AgentToolController(tools);
  private final UserId user = new UserId(UUID.randomUUID());
  private final Principal principal = () -> user.value().toString();

  @Test
  void catalogIsScopedToTheAuthenticatedUser() {
    ToolStatus status = status("INSTALLED");
    when(tools.catalog(user)).thenReturn(List.of(status));

    assertEquals(List.of(status), controller.catalog(principal));

    verify(tools).catalog(user);
  }

  @Test
  void installAndUpdateTargetOnlyTheAuthenticatedUsersTool() {
    ToolStatus installing = status("INSTALLING");
    ToolStatus updating = status("INSTALLING");
    when(tools.installOpenCode(user)).thenReturn(installing);
    when(tools.updateOpenCode(user)).thenReturn(updating);

    assertEquals(installing, controller.installOpenCode(principal));
    assertEquals(updating, controller.updateOpenCode(principal));

    verify(tools).installOpenCode(user);
    verify(tools).updateOpenCode(user);
  }

  @Test
  void removalPreservesTheServiceOwnedProfileLifecycle() {
    ToolStatus removed = status("NOT_INSTALLED");
    when(tools.removeOpenCode(user)).thenReturn(removed);

    assertEquals(removed, controller.removeOpenCode(principal));

    verify(tools).removeOpenCode(user);
  }

  private static ToolStatus status(String status) {
    return new ToolStatus("opencode", "OpenCode", "1.19.5", "x64", status, 10, 20, 0, "");
  }
}
