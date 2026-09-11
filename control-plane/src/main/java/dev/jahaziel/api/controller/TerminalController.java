/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.dto.TerminalSessionDto;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** User-facing terminal session endpoints. PTY relay itself runs in the workspace VM. */
@RestController
@RequestMapping("/api/v1")
public class TerminalController {
  private final TerminalService terminals;

  /**
   * Creates the controller.
   *
   * @param terminals the terminal registry
   */
  public TerminalController(TerminalService terminals) {
    this.terminals = terminals;
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
    return TerminalSessionDto.from(
        terminals.open(new WorkspaceId(id), userId(principal), rows, cols));
  }

  /**
   * Lists a workspace's sessions.
   *
   * @param id the workspace identifier
   * @return the session DTOs
   */
  @GetMapping("/workspaces/{id}/terminals")
  public List<TerminalSessionDto> list(@PathVariable UUID id) {
    return terminals.listByWorkspace(new WorkspaceId(id)).stream()
        .map(TerminalSessionDto::from)
        .toList();
  }

  /**
   * Closes a session.
   *
   * @param sessionId the session identifier
   */
  @DeleteMapping("/terminals/{sessionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void close(@PathVariable UUID sessionId) {
    terminals.close(new TerminalSessionId(sessionId));
  }

  private static UserId userId(Principal principal) {
    try {
      return new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }
}
