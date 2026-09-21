/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A node was lost.
 *
 * @param nodeId the node
 * @param reason why the node is considered lost
 * @param occurredAt when the event occurred
 */
public record NodeLostEvent(String nodeId, String reason, Instant occurredAt)
    implements DomainEvent {
  public NodeLostEvent {
    Objects.requireNonNull(nodeId, "nodeId");
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
