/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.dto.MetricsDto;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.monitoring.api.MetricsService;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
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
  private final String appDomain;

  /**
   * Creates the controller.
   *
   * @param runtime the runtime lifecycle
   * @param workspaces the workspace lookup for responses
   * @param metrics the metric rollups
   * @param appDomain the public domain
   */
  public RuntimeController(
      RuntimeService runtime,
      WorkspaceService workspaces,
      MetricsService metrics,
      @Value("${jahaziel.domain}") String appDomain) {
    this.runtime = runtime;
    this.workspaces = workspaces;
    this.metrics = metrics;
    this.appDomain = appDomain;
  }

  /**
   * Starts a workspace.
   *
   * @param id the workspace identifier
   * @return the workspace DTO
   */
  @PostMapping("/workspaces/{id}/start")
  public WorkspaceDto start(@PathVariable UUID id) {
    runtime.start(new WorkspaceId(id));
    return WorkspaceDto.from(workspaces.describe(new WorkspaceId(id)), appDomain);
  }

  /**
   * Stops a workspace, preserving the home subvolume.
   *
   * @param id the workspace identifier
   * @return the workspace DTO
   */
  @PostMapping("/workspaces/{id}/stop")
  public WorkspaceDto stop(@PathVariable UUID id) {
    runtime.stop(new WorkspaceId(id));
    return WorkspaceDto.from(workspaces.describe(new WorkspaceId(id)), appDomain);
  }

  /**
   * Restarts a workspace from its preserved home.
   *
   * @param id the workspace identifier
   * @return the workspace DTO
   */
  @PostMapping("/workspaces/{id}/restart")
  public WorkspaceDto restart(@PathVariable UUID id) {
    runtime.restart(new WorkspaceId(id));
    return WorkspaceDto.from(workspaces.describe(new WorkspaceId(id)), appDomain);
  }

  /**
   * Returns current metrics. Zeros until the sampler records the first rollup.
   *
   * @param id the workspace identifier
   * @return the metrics DTO
   */
  @GetMapping("/workspaces/{id}/metrics")
  public MetricsDto metrics(@PathVariable UUID id) {
    return metrics.latest(new WorkspaceId(id)).map(MetricsDto::from).orElseGet(MetricsDto::zero);
  }
}
