/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A node registered.
 *
 * @param nodeId the node
 * @param occurredAt when the event occurred
 */
public record NodeRegisteredEvent(String nodeId, Instant occurredAt) implements DomainEvent {
  public NodeRegisteredEvent {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
