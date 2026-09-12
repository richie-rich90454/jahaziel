/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.api;

import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;

/**
 * Workspace runtime lifecycle. Storage prepares, the VM boots, and the home subvolume always
 * survives: stop and restart never lose files.
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
   * Stops a workspace gracefully, preserving the home subvolume.
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
