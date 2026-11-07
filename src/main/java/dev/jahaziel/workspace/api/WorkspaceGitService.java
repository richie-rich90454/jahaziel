/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.List;

/** Fixed, workspace-scoped Git operations for the browser workbench. */
public interface WorkspaceGitService {
  Status status(WorkspaceId workspaceId);

  Diff diff(WorkspaceId workspaceId, String path);

  Status stage(WorkspaceId workspaceId, String path);

  Status unstage(WorkspaceId workspaceId, String path);

  CommitResult commit(WorkspaceId workspaceId, String message);

  record Status(boolean repository, String branch, List<Change> changes) {}

  record Change(String path, String indexStatus, String worktreeStatus) {}

  record Diff(String path, String content) {}

  record CommitResult(String commit, String summary) {}
}
