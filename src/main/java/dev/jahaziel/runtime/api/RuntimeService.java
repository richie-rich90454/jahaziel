/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.api;

import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;

/**
 * Workspace runtime lifecycle. The backend prepares the workspace home, and that home survives
 * stop, restart, and resume.
 */
public interface RuntimeService {
  /**
   * Starts a workspace runtime and persists each lifecycle transition.
   *
   * @param id the workspace identifier
   * @return the workspace while its runtime is starting
   */
  Workspace start(WorkspaceId id);

  /**
   * Stops a workspace gracefully, preserving the home folder.
   *
   * @param id the workspace identifier
   * @return the suspended workspace
   */
  Workspace stop(WorkspaceId id);

  /**
   * Restarts a workspace runtime from its preserved home.
   *
   * @param id the workspace identifier
   * @return the workspace while its runtime is starting
   */
  Workspace restart(WorkspaceId id);

  /**
   * Resumes a suspended workspace runtime on the preserved home.
   *
   * @param id the workspace identifier
   * @return the active workspace
   */
  Workspace resume(WorkspaceId id);

  /**
   * Stops workspace-owned processes, removes its port bindings, and destroys the backend handle.
   * The home folder survives; the caller deletes the workspace record itself.
   *
   * @param id the workspace identifier
   */
  void destroy(WorkspaceId id);

  /**
   * Marks a started workspace ready after runtime preparation completes.
   *
   * @param id the workspace identifier
   * @return the ready workspace
   */
  Workspace markReady(WorkspaceId id);

  /**
   * Marks a ready workspace active on first user session.
   *
   * @param id the workspace identifier
   * @return the active workspace
   */
  Workspace markActive(WorkspaceId id);
}
