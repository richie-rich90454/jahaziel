/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A workspace was deleted.
 *
 * @param workspaceId the workspace
 * @param occurredAt when the event occurred
 */
public record WorkspaceDeletedEvent(UUID workspaceId, Instant occurredAt) implements DomainEvent {
  public WorkspaceDeletedEvent {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
