/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.debug.api.DebugService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authorized workspace-scoped debugger lifecycle operations. */
@RestController
@RequestMapping("/api/v1/workspaces/{id}/debug-sessions")
public final class DebugSessionController {
  private final DebugService debug;
  private final AuthorizationService authorization;

  public DebugSessionController(DebugService debug, AuthorizationService authorization) {
    this.debug = debug;
    this.authorization = authorization;
  }

  @GetMapping
  public List<DebugService.Session> list(@PathVariable UUID id, Principal principal) {
    return debug.list(require(principal, id, "read"));
  }

  @PostMapping
  public DebugService.Session start(
      @PathVariable UUID id, @RequestBody DebugService.StartRequest request, Principal principal) {
    return debug.start(require(principal, id, "write"), request);
  }

  @PostMapping("/attach")
  public DebugService.Session attach(
      @PathVariable UUID id,
      @RequestBody DebugService.AttachRequest request,
      Principal principal) {
    return debug.attachToTarget(require(principal, id, "write"), request);
  }

  @GetMapping("/{sessionId}")
  public DebugService.Session get(
      @PathVariable UUID id, @PathVariable UUID sessionId, Principal principal) {
    return debug.get(require(principal, id, "read"), sessionId);
  }

  @PostMapping("/{sessionId}/stop")
  public DebugService.Session stop(
      @PathVariable UUID id, @PathVariable UUID sessionId, Principal principal) {
    return debug.stop(require(principal, id, "write"), sessionId);
  }

  private WorkspaceId require(Principal principal, UUID id, String permission) {
    UserId user;
    try {
      user = new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException | NullPointerException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
    WorkspaceId workspace = new WorkspaceId(id);
    if (!authorization.isAllowed(user, permission, workspace)) {
      throw new AccessDeniedException(user, permission);
    }
    return workspace;
  }
}
