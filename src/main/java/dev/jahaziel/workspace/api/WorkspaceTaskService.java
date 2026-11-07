/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Server-managed template setup and run tasks for one workspace. */
public interface WorkspaceTaskService {
  /** Associates the server-owned template with a newly created workspace. */
  void initialize(WorkspaceId workspaceId, String templateName);

  /** Returns current setup progress and bounded output. */
  SetupStatus setupStatus(WorkspaceId workspaceId);

  /** Starts the template's setup steps again after a failure. */
  SetupStatus retrySetup(WorkspaceId workspaceId);

  /** Returns the safe task labels that may be run; no command data is exposed. */
  List<Task> tasks(WorkspaceId workspaceId);

  /**
   * Returns packs declared by this workspace's server-managed template. An empty list means the
   * template places no restriction on the server catalog.
   */
  default List<String> supportedLanguagePacks(WorkspaceId workspaceId) {
    return List.of();
  }

  /** Starts a declared task by its server-owned id. */
  TaskRun startTask(WorkspaceId workspaceId, String taskId);

  /** Reads one run, including its bounded output. */
  TaskRun taskRun(UUID runId);

  /** Stops a running declared task belonging to the specified workspace. */
  TaskRun stopTask(WorkspaceId workspaceId, UUID runId);

  /** Stops template setup and run processes when the workspace is stopped or destroyed. */
  void closeByWorkspace(WorkspaceId workspaceId);

  /** Allows setup retries after the workspace has started again. */
  void allowByWorkspace(WorkspaceId workspaceId);

  /** Stops every setup and run process during server shutdown. */
  void closeAll();

  record SetupStatus(
      String status, String output, String error, Instant updatedAt, UUID correlationId) {}

  record Task(String id, String label, Integer port) {}

  record TaskRun(
      UUID id,
      UUID workspaceId,
      String taskId,
      String label,
      String status,
      String output,
      Integer exitCode,
      Instant startedAt,
      Instant finishedAt) {}
}
