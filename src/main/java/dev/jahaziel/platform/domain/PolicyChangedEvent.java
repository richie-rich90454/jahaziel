/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A policy changed.
 *
 * @param scope the policy scope, global, user or workspace
 * @param scopeId the scoped identifier, empty for global scope
 * @param occurredAt when the event occurred
 */
public record PolicyChangedEvent(String scope, String scopeId, Instant occurredAt)
    implements DomainEvent {
  public PolicyChangedEvent {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("scope must not be blank");
    }
    Objects.requireNonNull(scopeId, "scopeId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
