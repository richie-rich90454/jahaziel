/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.UUID;

/**
 * Prepares, snapshots and destroys workspace storage. Identifiers are plain UUIDs so this module
 * stays dependency-free; the domain value objects map to them at the boundary.
 */
@JahazielSpi(version = 1)
public interface WorkspaceStorage {
  /**
   * Prepares storage for a workspace. Homes are keyed by workspace id; the owner is retained for
   * quota, audit, and policy decisions.
   *
   * @param workspaceId the workspace to prepare
   * @param ownerId the owning user
   * @param image the base image to provision from
   * @param quota the logical quota for the home folder
   * @return the storage handle
   */
  WorkspaceHandle prepare(UUID workspaceId, UUID ownerId, ImageRef image, QuotaBytes quota);

  /**
   * Destroys workspace storage.
   *
   * @param handle the storage handle
   */
  void destroy(WorkspaceHandle handle);

  /**
   * Destroys a workspace while optionally retaining its snapshots.
   *
   * <p>Implementations that store snapshots separately can override this method to preserve them.
   *
   * @param handle the storage handle
   * @param keepSnapshots whether snapshots should survive workspace removal
   */
  default void destroy(WorkspaceHandle handle, boolean keepSnapshots) {
    destroy(handle);
  }

  /**
   * Snapshots a workspace home folder.
   *
   * @param handle the storage handle
   * @param tag the snapshot tag
   * @return the snapshot handle
   */
  SnapshotHandle snapshot(WorkspaceHandle handle, SnapshotTag tag);

  /**
   * Deletes a snapshot folder.
   *
   * @param snapshot the snapshot handle
   */
  void deleteSnapshot(SnapshotHandle snapshot);

  /**
   * Restores a workspace home folder from a snapshot.
   *
   * @param handle the storage handle
   * @param snapshot the snapshot to restore
   */
  void restore(WorkspaceHandle handle, SnapshotHandle snapshot);

  /**
   * Measures storage consumption.
   *
   * @param handle the storage handle
   * @return the current usage
   */
  StorageUsage usage(WorkspaceHandle handle);
}
