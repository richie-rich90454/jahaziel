/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;

/** Thrown when a workspace does not exist. */
public final class WorkspaceNotFoundException extends RuntimeException {
  private final WorkspaceId workspaceId;

  /**
   * Creates the exception.
   *
   * @param workspaceId the missing workspace identifier
   */
  public WorkspaceNotFoundException(WorkspaceId workspaceId) {
    super("Workspace " + workspaceId + " not found");
    this.workspaceId = workspaceId;
  }

  /** Returns the missing workspace identifier. */
  public WorkspaceId workspaceId() {
    return workspaceId;
  }
}
