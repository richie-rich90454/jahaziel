/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * A workspace was created.
 *
 * @param workspaceId the workspace
 * @param ownerId the owning user
 * @param occurredAt when the event occurred
 */
public record WorkspaceCreatedEvent(WorkspaceId workspaceId, UserId ownerId, Instant occurredAt)
    implements DomainEvent {
  public WorkspaceCreatedEvent {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
