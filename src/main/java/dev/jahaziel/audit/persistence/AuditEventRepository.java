/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository for AuditEventEntity persistence. */
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, AuditEventEntity.Id> {
  /**
   * Returns event rows newest first. The timestamp lives on the embedded id, so the ordering is
   * explicit JPQL rather than a derived query.
   *
   * @param pageable the page to return
   * @return the rows newest first
   */
  @Query("SELECT e FROM AuditEventEntity e ORDER BY e.id.timestamp DESC")
  List<AuditEventEntity> findLatest(Pageable pageable);
}
