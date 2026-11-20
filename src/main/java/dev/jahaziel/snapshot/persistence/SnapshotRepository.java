/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for SnapshotEntity; interface is required by Spring Data. */
public interface SnapshotRepository extends JpaRepository<SnapshotEntity, UUID> {
    /**
     * Finds all snapshots of a workspace.
     *
     * @param workspaceId the workspace identifier
     * @return the snapshots
     */
    List<SnapshotEntity> findByWorkspaceId(UUID workspaceId);
}
