/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

/**
 * Logical quota of a workspace. Only actual consumption matters physically.
 *
 * @param logicalBytes logical byte limit of the home folder
 * @param inodeLimit inode limit of the home folder
 */
public record WorkspaceQuota(long logicalBytes, long inodeLimit) {
  public WorkspaceQuota {
    if (logicalBytes < 0) {
      throw new IllegalArgumentException("logicalBytes must not be negative");
    }
    if (inodeLimit < 0) {
      throw new IllegalArgumentException("inodeLimit must not be negative");
    }
  }

  /** Returns the default quota for a new workspace. */
  public static WorkspaceQuota defaults() {
    return new WorkspaceQuota(21474836480L, 1000000L);
  }
}
