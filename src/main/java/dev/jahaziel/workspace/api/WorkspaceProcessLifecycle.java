/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;

/**
 * Lifecycle port for terminating interactive server processes when a workspace stops or is removed.
 */
public abstract class WorkspaceProcessLifecycle {
    /** Stops all registered processes belonging to the workspace. */
    public abstract void closeByWorkspace(WorkspaceId workspaceId);

    /** Allows new interactive processes after a workspace has successfully started. */
    public void allowByWorkspace(WorkspaceId workspaceId) {}

    /** Stops processes and releases workspace-scoped process metadata before deletion. */
    public void deleteWorkspace(WorkspaceId workspaceId) {
        closeByWorkspace(workspaceId);
    }
}
