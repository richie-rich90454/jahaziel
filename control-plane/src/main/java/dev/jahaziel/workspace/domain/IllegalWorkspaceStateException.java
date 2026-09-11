/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

/** Thrown when an action is not legal in the current workspace state. */
public final class IllegalWorkspaceStateException extends IllegalStateException {
  private final WorkspaceId workspaceId;
  private final WorkspaceState state;
  private final String action;

  /**
   * Creates the exception.
   *
   * @param workspaceId the workspace identifier
   * @param state the state the workspace was in
   * @param action the attempted action
   */
  public IllegalWorkspaceStateException(
      WorkspaceId workspaceId, WorkspaceState state, String action) {
    super("Workspace " + workspaceId + " cannot " + action + " while " + state);
    this.workspaceId = workspaceId;
    this.state = state;
    this.action = action;
  }

  /** Returns the workspace identifier. */
  public WorkspaceId workspaceId() {
    return workspaceId;
  }

  /** Returns the state the workspace was in. */
  public WorkspaceState state() {
    return state;
  }

  /** Returns the attempted action. */
  public String action() {
    return action;
  }
}
