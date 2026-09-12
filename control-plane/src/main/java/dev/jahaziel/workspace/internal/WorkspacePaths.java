/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.Objects;

/**
 * Deterministic host paths shared by the control plane and the node agent. Both sides compute the
 * same layout from the same inputs; neither stores redundant copies.
 */
public final class WorkspacePaths {
  private WorkspacePaths() {}

  /**
   * Returns the jail directory.
   *
   * @param storageRoot the storage root
   * @param workspaceId the workspace identifier
   * @return the jail path
   */
  public static String jailPath(String storageRoot, WorkspaceId workspaceId) {
    return root(storageRoot)
        + "/jail/"
        + Objects.requireNonNull(workspaceId, "workspaceId").value();
  }

  /**
   * Returns the persistent home folder path. Homes are per user.
   *
   * @param storageRoot the storage root
   * @param ownerId the owning user identifier
   * @return the home path
   */
  public static String homePath(String storageRoot, UserId ownerId) {
    return root(storageRoot)
        + "/workspaces/"
        + Objects.requireNonNull(ownerId, "ownerId").value()
        + "/home";
  }

  private static String root(String storageRoot) {
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    return storageRoot;
  }
}
