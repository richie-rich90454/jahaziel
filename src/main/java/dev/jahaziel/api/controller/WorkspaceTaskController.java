/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authorized setup and declared task operations for workspace templates. */
@RestController
@RequestMapping("/api/v1/workspaces/{id}")
public final class WorkspaceTaskController {
  private final WorkspaceTaskService tasks;
  private final AuthorizationService auth;

  public WorkspaceTaskController(WorkspaceTaskService tasks, AuthorizationService auth) {
    this.tasks = tasks;
    this.auth = auth;
  }

  @GetMapping("/setup")
  public WorkspaceTaskService.SetupStatus setup(@PathVariable UUID id, Principal principal) {
    return tasks.setupStatus(require(principal, id, "read"));
  }

  @PostMapping("/setup/retry")
  public WorkspaceTaskService.SetupStatus retrySetup(@PathVariable UUID id, Principal principal) {
    return tasks.retrySetup(require(principal, id, "write"));
  }

  @GetMapping("/tasks")
  public List<WorkspaceTaskService.Task> tasks(@PathVariable UUID id, Principal principal) {
    return tasks.tasks(require(principal, id, "read"));
  }

  @PostMapping("/tasks/{taskId}/run")
  public WorkspaceTaskService.TaskRun run(
      @PathVariable UUID id, @PathVariable String taskId, Principal principal) {
    return tasks.startTask(require(principal, id, "write"), taskId);
  }

  @GetMapping("/task-runs/{runId}")
  public WorkspaceTaskService.TaskRun taskRun(
      @PathVariable UUID id, @PathVariable UUID runId, Principal principal) {
    WorkspaceId workspace = require(principal, id, "read");
    WorkspaceTaskService.TaskRun run = tasks.taskRun(runId);
    if (!run.workspaceId().equals(workspace.value())) {
      throw new IllegalArgumentException("task run not found");
    }
    return run;
  }

  @GetMapping("/task-runs/latest")
  public ResponseEntity<WorkspaceTaskService.TaskRun> latestTaskRun(
      @PathVariable UUID id, Principal principal) {
    WorkspaceId workspace = require(principal, id, "read");
    WorkspaceTaskService.TaskRun run = tasks.latestTaskRun(workspace);
    return run == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(run);
  }

  @PostMapping("/task-runs/{runId}/stop")
  public WorkspaceTaskService.TaskRun stopTask(
      @PathVariable UUID id, @PathVariable UUID runId, Principal principal) {
    WorkspaceId workspace = require(principal, id, "write");
    return tasks.stopTask(workspace, runId);
  }

  private WorkspaceId require(Principal principal, UUID id, String permission) {
    UserId user;
    try {
      user = new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
    WorkspaceId workspace = new WorkspaceId(id);
    if (!auth.isAllowed(user, permission, workspace))
      throw new AccessDeniedException(user, permission);
    return workspace;
  }
}
