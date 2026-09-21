/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for WorkspaceEntity persistence. */
public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {
  /**
   * Finds all workspaces of an owner.
   *
   * @param ownerUserId the owning user identifier
   * @return the workspaces
   */
  List<WorkspaceEntity> findByOwnerUserId(UUID ownerUserId);
}
