/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.time.Instant;
import java.util.stream.Stream;

/** Append-only audit store. Only retention deletes, never row deletes. */
@JahazielSpi(version = 1)
public interface AuditSink {
  /**
   * Writes one audit event.
   *
   * @param event the event to append
   */
  void write(AuditEvent event);

  /**
   * Queries stored audit events.
   *
   * @param query the query criteria
   * @return the matching events
   */
  Stream<AuditEvent> query(AuditQuery query);

  /**
   * Deletes events before a cutoff. Retention only.
   *
   * @param cutoff events before this instant are deleted
   * @return the number of deleted events
   */
  int deleteBefore(Instant cutoff);
}
