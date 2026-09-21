/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.api;

import dev.jahaziel.audit.domain.AuditRecord;
import java.util.List;

/** Append-only audit log. Every write extends the hash chain. */
public interface AuditService {
  /**
   * Persists one event extending the hash chain.
   *
   * @param event the event to append, hash and link recomputed
   * @return the stored event with its chain link
   * @throws NullPointerException when the event is null
   */
  AuditRecord append(AuditRecord event);

  /**
   * Returns the newest events first.
   *
   * @param limit the maximum events to return
   * @return the newest events first
   * @throws IllegalArgumentException when the limit is not positive
   */
  List<AuditRecord> latest(int limit);
}
