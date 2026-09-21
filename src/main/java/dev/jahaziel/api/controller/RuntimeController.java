/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.WorkspaceDtoFactory;
import dev.jahaziel.api.dto.MetricsDto;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.monitoring.api.MetricsService;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User-facing runtime lifecycle and metrics endpoints. */
@RestController
@RequestMapping("/api/v1")
public class RuntimeController {
  private final RuntimeService runtime;
  private final WorkspaceService workspaces;
  private final MetricsService metrics;
  private final AuthorizationService auth;
  private final WorkspaceDtoFactory dtos;

  /**
   * Creates the controller.
   *
   * @param runtime the runtime lifecycle
   * @param workspaces the workspace lookup for responses
   * @param metrics the metric rollups
   * @param auth the authorization checks
   */
  public RuntimeController(
      RuntimeService runtime,
      WorkspaceService workspaces,
      MetricsService metrics,
      AuthorizationService auth,
      WorkspaceDtoFactory dtos) {
    this.runtime = runtime;
    this.workspaces = workspaces;
    this.metrics = metrics;
    this.auth = auth;
    this.dtos = dtos;
  }

  /**
   * Starts a workspace.
   *
   * @param id the workspace identifier
   * @param principal the acting user
   * @return the workspace DTO
   */
  @PostMapping("/workspaces/{id}/start")
  public WorkspaceDto start(@PathVariable UUID id, Principal principal) {
    WorkspaceId workspace = require(principal, id, "write");
    runtime.start(workspace);
    return dtos.forUser(userId(principal), workspaces.describe(workspace));
  }

  /**
   * Stops a workspace, preserving the home folder.
   *
   * @param id the workspace identifier
   * @param principal the acting user
   * @return the workspace DTO
   */
  @PostMapping("/workspaces/{id}/stop")
  public WorkspaceDto stop(@PathVariable UUID id, Principal principal) {
    WorkspaceId workspace = require(principal, id, "write");
    runtime.stop(workspace);
    return dtos.forUser(userId(principal), workspaces.describe(workspace));
  }

  /**
   * Restarts a workspace from its preserved home.
   *
   * @param id the workspace identifier
   * @param principal the acting user
   * @return the workspace DTO
   */
  @PostMapping("/workspaces/{id}/restart")
  public WorkspaceDto restart(@PathVariable UUID id, Principal principal) {
    WorkspaceId workspace = require(principal, id, "write");
    runtime.restart(workspace);
    return dtos.forUser(userId(principal), workspaces.describe(workspace));
  }

  /**
   * Returns current metrics. Zeros until the sampler records the first rollup.
   *
   * @param id the workspace identifier
   * @param principal the acting user
   * @return the metrics DTO
   */
  @GetMapping("/workspaces/{id}/metrics")
  public MetricsDto metrics(@PathVariable UUID id, Principal principal) {
    WorkspaceId workspace = require(principal, id, "read");
    return metrics.latest(workspace).map(MetricsDto::from).orElseGet(MetricsDto::zero);
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
