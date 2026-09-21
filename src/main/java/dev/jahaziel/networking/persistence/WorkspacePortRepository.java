/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for WorkspacePortEntity persistence. */
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
