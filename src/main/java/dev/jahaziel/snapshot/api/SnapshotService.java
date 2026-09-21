/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.api;

import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.List;

/** Workspace home snapshots. Sizes and manifest hashes arrive with verification. */
public interface SnapshotService {
  /**
   * Creates a snapshot.
   *
   * @param workspaceId the workspace
   * @param tag the human tag, unique per workspace
   * @param kind how the snapshot was triggered
   * @param createdBy who triggered it
   * @return the metadata
   */
  SnapshotMetadata create(WorkspaceId workspaceId, String tag, SnapshotKind kind, String createdBy);

  /**
   * Lists a workspace's snapshots newest first.
   *
   * @param workspaceId the workspace
   * @return the metadata
   */
  List<SnapshotMetadata> list(WorkspaceId workspaceId);

  /**
   * Restores a workspace home from a snapshot.
   *
   * @param workspaceId the workspace
   * @param snapshotId the snapshot
   */
  void restore(WorkspaceId workspaceId, SnapshotId snapshotId);

  /**
   * Deletes a snapshot.
   *
   * @param snapshotId the snapshot
   */
  void delete(SnapshotId snapshotId);
}
