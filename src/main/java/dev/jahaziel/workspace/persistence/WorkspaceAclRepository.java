/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for WorkspaceAclEntity persistence. */
public interface WorkspaceAclRepository
    extends JpaRepository<WorkspaceAclEntity, WorkspaceAclEntity.Id> {
  /**
   * Finds all grants on a workspace.
   *
   * @param workspaceId the workspace identifier
   * @return the grants
   */
  List<WorkspaceAclEntity> findByIdWorkspaceId(UUID workspaceId);
}
