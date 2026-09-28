/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.api;

import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;

/**
 * Workspace runtime lifecycle. Storage prepares, the VM boots, and the home folder always survives:
 * stop and restart never lose files.
 */
public interface RuntimeService {
  /**
   * Starts a workspace: prepares storage, boots the VM, persists every transition.
   *
   * @param id the workspace identifier
   * @return the booting workspace
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
   * Restarts a workspace: stop, reboot into a fresh cycle, start from the preserved home.
   *
   * @param id the workspace identifier
   * @return the booting workspace
   */
  Workspace restart(WorkspaceId id);

  /**
   * Resumes a suspended workspace with a fresh VM on the preserved home.
   *
   * @param id the workspace identifier
   * @return the active workspace
   */
  Workspace resume(WorkspaceId id);

  /**
   * Tears down everything a workspace owns: stops the IDE server, removes every port binding, and
   * destroys the backend handle. The home folder survives; the caller deletes the record itself, so
   * reads fail fast afterwards instead of serving half-deleted state.
   *
   * @param id the workspace identifier
   */
  void destroy(WorkspaceId id);

  /**
   * Marks a booted workspace ready after the guest agent registers.
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
