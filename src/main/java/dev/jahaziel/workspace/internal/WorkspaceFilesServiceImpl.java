/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.Objects;

/** Creates a filesystem capability scoped to exactly one workspace. */
public final class WorkspaceFilesServiceImpl implements WorkspaceFiles {
  private final String storageRoot;

  public WorkspaceFilesServiceImpl(String storageRoot) {
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    this.storageRoot = storageRoot;
  }

  @Override
  public Operations forWorkspace(WorkspaceId workspaceId) {
    return new WorkspaceFilesImpl(storageRoot, Objects.requireNonNull(workspaceId, "workspaceId"));
  }
}
