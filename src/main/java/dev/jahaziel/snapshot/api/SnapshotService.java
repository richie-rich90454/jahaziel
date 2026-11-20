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

/** Workspace home snapshots. */
public abstract class SnapshotService {
    /**
     * Creates a snapshot.
     *
     * @param workspaceId the workspace
     * @param tag the human tag, unique per workspace
     * @param kind how the snapshot was triggered
     * @param createdBy who triggered it
     * @return the metadata
     */
    public abstract SnapshotMetadata create(
            WorkspaceId workspaceId, String tag, SnapshotKind kind, String createdBy);

    /**
     * Lists a workspace's snapshots newest first.
     *
     * @param workspaceId the workspace
     * @return the metadata
     */
    public abstract List<SnapshotMetadata> list(WorkspaceId workspaceId);

    /**
     * Restores a workspace home from a snapshot.
     *
     * @param workspaceId the workspace
     * @param snapshotId the snapshot
     */
    public abstract void restore(WorkspaceId workspaceId, SnapshotId snapshotId);

    /**
     * Deletes a snapshot.
     *
     * @param workspaceId the workspace that owns the snapshot
     * @param snapshotId the snapshot
     */
    public abstract void delete(WorkspaceId workspaceId, SnapshotId snapshotId);
}
