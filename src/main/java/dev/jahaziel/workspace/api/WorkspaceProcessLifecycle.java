/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;

/**
 * Lifecycle port for terminating interactive server processes when a workspace stops or is removed.
 */
public interface WorkspaceProcessLifecycle {
  /** Stops all registered processes belonging to the workspace. */
  void closeByWorkspace(WorkspaceId workspaceId);

  /** Allows new interactive processes after a workspace has successfully started. */
  default void allowByWorkspace(WorkspaceId workspaceId) {}

  /** Stops processes and releases workspace-scoped process metadata before deletion. */
  default void deleteWorkspace(WorkspaceId workspaceId) {
    closeByWorkspace(workspaceId);
  }
}
