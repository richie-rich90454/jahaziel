/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;

/** Server-only workspace directory lookup for process services. */
public interface WorkspaceDirectoryService {
  /** Returns the real, confined home directory for a workspace. */
  Path home(WorkspaceId workspaceId);
}
