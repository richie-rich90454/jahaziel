/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

/** Optimistic file save failed because the current file version differs from the editor version. */
public final class WorkspaceFileConflictException extends RuntimeException {
  public WorkspaceFileConflictException(String message) {
    super(message);
  }
}
