/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A workspace runtime stopped.
 *
 * @param workspaceId the workspace
 * @param occurredAt when the event occurred
 */
public record RuntimeStoppedEvent(UUID workspaceId, Instant occurredAt) implements DomainEvent {
  public RuntimeStoppedEvent {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
