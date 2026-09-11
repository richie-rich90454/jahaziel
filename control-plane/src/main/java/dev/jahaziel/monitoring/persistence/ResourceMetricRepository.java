/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository for ResourceMetricEntity persistence. */
public interface ResourceMetricRepository
    extends JpaRepository<ResourceMetricEntity, ResourceMetricEntity.Id> {
  /**
   * Returns metric rows newest first. The timestamp lives on the embedded id, so the ordering is
   * explicit JPQL rather than a derived query.
   *
   * @param workspaceId the workspace identifier
   * @param pageable the page to return
   * @return the rows newest first
   */
  @Query(
      "SELECT e FROM ResourceMetricEntity e WHERE e.id.workspaceId = :workspaceId ORDER BY e.id.hourStart DESC")
  List<ResourceMetricEntity> findLatest(UUID workspaceId, Pageable pageable);
}
