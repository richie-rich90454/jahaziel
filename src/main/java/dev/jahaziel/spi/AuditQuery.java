/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Criteria for querying the audit store.
 *
 * @param from earliest timestamp, empty for no lower bound
 * @param to latest timestamp, empty for no upper bound
 * @param actor actor filter, empty for all actors
 * @param action action filter, empty for all actions
 * @param limit maximum events to return
 */
public record AuditQuery(
    Optional<Instant> from,
    Optional<Instant> to,
    Optional<String> actor,
    Optional<String> action,
    int limit) {
  public AuditQuery {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(action, "action");
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be positive");
    }
  }
}
