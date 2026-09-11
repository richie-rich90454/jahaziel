/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * A workspace was deleted.
 *
 * @param workspaceId the workspace
 * @param occurredAt when the event occurred
 */
public record WorkspaceDeletedEvent(WorkspaceId workspaceId, Instant occurredAt)
    implements DomainEvent {
  public WorkspaceDeletedEvent {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
