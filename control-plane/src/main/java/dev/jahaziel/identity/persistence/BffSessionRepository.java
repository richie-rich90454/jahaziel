/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** Repository for BffSessionEntity persistence. */
public interface BffSessionRepository extends JpaRepository<BffSessionEntity, String> {
  /**
   * Removes sessions that expired before a cutoff.
   *
   * @param cutoff the exclusive expiry bound
   * @return the number of rows removed
   */
  @Transactional
  long deleteByExpiresAtBefore(Instant cutoff);
}
