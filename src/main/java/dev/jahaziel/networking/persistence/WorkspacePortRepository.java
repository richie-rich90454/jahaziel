/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for WorkspacePortEntity; interface is required by Spring Data. */
public interface WorkspacePortRepository
        extends JpaRepository<WorkspacePortEntity, WorkspacePortEntity.Id> {
    /**
     * Finds all bindings of a workspace.
     *
     * @param workspaceId the workspace identifier
     * @return the bindings
     */
    List<WorkspacePortEntity> findByIdWorkspaceId(UUID workspaceId);
}
