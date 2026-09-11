/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * A workspace runtime stopped.
 *
 * @param workspaceId the workspace
 * @param occurredAt when the event occurred
 */
public record RuntimeStoppedEvent(WorkspaceId workspaceId, Instant occurredAt)
    implements DomainEvent {
  public RuntimeStoppedEvent {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
