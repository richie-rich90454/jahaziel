/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A workspace was created.
 *
 * @param workspaceId the workspace
 * @param ownerId the owning user
 * @param occurredAt when the event occurred
 */
public record WorkspaceCreatedEvent(UUID workspaceId, UUID ownerId, Instant occurredAt)
    implements DomainEvent {
  public WorkspaceCreatedEvent {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
