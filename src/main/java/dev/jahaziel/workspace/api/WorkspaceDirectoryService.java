/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;

import java.nio.file.Path;

/** Server-only workspace directory lookup for process services. */
public abstract class WorkspaceDirectoryService {
    /** Returns the real, confined home directory for a workspace. */
    public abstract Path home(WorkspaceId workspaceId);
}
