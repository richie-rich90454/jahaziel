/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.agenttools.api.AgentToolNotInstalledException;
import dev.jahaziel.agenttools.api.AgentToolService;
import dev.jahaziel.api.dto.TerminalSessionDto;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** User-facing terminal session endpoints. PTYs run in the trusted host account. */
@RestController
@RequestMapping("/api/v1")
public class TerminalController {
  private final TerminalService terminals;
  private final AuthorizationService auth;
  private final AgentToolService agentTools;

  /**
   * Creates the controller.
   *
   * @param terminals the terminal registry
   * @param auth the authorization checks
   */
  public TerminalController(TerminalService terminals, AuthorizationService auth) {
    this(terminals, auth, null);
  }

  @Autowired
  public TerminalController(
      TerminalService terminals, AuthorizationService auth, AgentToolService agentTools) {
    this.terminals = terminals;
    this.auth = auth;
    this.agentTools = agentTools;
  }

  /**
   * Opens a session.
   *
   * @param id the workspace identifier
   * @param rows terminal rows
   * @param cols terminal columns
   * @param principal the acting user
   * @return the session DTO
   */
  @PostMapping("/workspaces/{id}/terminals")
  @ResponseStatus(HttpStatus.CREATED)
  public TerminalSessionDto open(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "30") int rows,
      @RequestParam(defaultValue = "120") int cols,
      Principal principal) {
    WorkspaceId workspace = require(principal, id, "write");
    return TerminalSessionDto.from(terminals.open(workspace, userId(principal), rows, cols));
  }

  /** Opens a terminal and starts the server-managed OpenCode command. */
  @PostMapping("/workspaces/{id}/terminals/opencode")
  @ResponseStatus(HttpStatus.CREATED)
  public TerminalSessionDto openOpenCode(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "30") int rows,
      @RequestParam(defaultValue = "120") int cols,
      Principal principal) {
    UserId user = userId(principal);
    WorkspaceId workspace = require(principal, id, "write");
    if (agentTools == null
        || agentTools.catalog(user).stream()
            .noneMatch(tool -> "opencode".equals(tool.id()) && "INSTALLED".equals(tool.status()))) {
      throw new AgentToolNotInstalledException();
    }
    var session = terminals.open(workspace, user, rows, cols);
    try {
      terminals.write(session.id(), "opencode\r");
      return TerminalSessionDto.from(session);
    } catch (RuntimeException failure) {
      terminals.close(session.id());
      throw failure;
    }
  }

  /**
   * Lists a workspace's sessions.
   *
   * @param id the workspace identifier
   * @param principal the acting user
   * @return the session DTOs
   */
  @GetMapping("/workspaces/{id}/terminals")
  public List<TerminalSessionDto> list(@PathVariable UUID id, Principal principal) {
    WorkspaceId workspace = require(principal, id, "read");
    return terminals.listByWorkspace(workspace).stream().map(TerminalSessionDto::from).toList();
  }

  /**
   * Closes a session.
   *
   * @param sessionId the session identifier
   * @param principal the acting user
   */
  @DeleteMapping("/terminals/{sessionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void close(@PathVariable UUID sessionId, Principal principal) {
    var session = terminals.get(new TerminalSessionId(sessionId));
    require(principal, session.workspaceId().value(), "write");
    terminals.close(new TerminalSessionId(sessionId));
  }

  private WorkspaceId require(Principal principal, UUID id, String permission) {
    UserId user = userId(principal);
    WorkspaceId workspace = new WorkspaceId(id);
    if (!auth.isAllowed(user, permission, workspace)) {
      throw new AccessDeniedException(user, permission);
    }
    return workspace;
  }

  private static UserId userId(Principal principal) {
    try {
      return new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }
}
