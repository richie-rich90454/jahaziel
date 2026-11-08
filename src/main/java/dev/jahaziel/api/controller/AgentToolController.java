/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.agenttools.api.AgentToolService;
import dev.jahaziel.agenttools.api.AgentToolService.ToolStatus;
import dev.jahaziel.identity.domain.UserId;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Per-user managed coding tool catalog and lifecycle. */
@RestController
@RequestMapping("/api/v1/me/tools")
public final class AgentToolController {
  private final AgentToolService tools;

  public AgentToolController(AgentToolService tools) {
    this.tools = tools;
  }

  @GetMapping
  public List<ToolStatus> catalog(Principal principal) {
    return tools.catalog(userId(principal));
  }

  @PostMapping("/opencode/install")
  public ToolStatus installOpenCode(Principal principal) {
    return tools.installOpenCode(userId(principal));
  }

  @PostMapping("/opencode/update")
  public ToolStatus updateOpenCode(Principal principal) {
    return tools.updateOpenCode(userId(principal));
  }

  @DeleteMapping("/opencode")
  public ToolStatus removeOpenCode(Principal principal) {
    return tools.removeOpenCode(userId(principal));
  }

  private static UserId userId(Principal principal) {
    try {
      return new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException | NullPointerException invalid) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }
}
