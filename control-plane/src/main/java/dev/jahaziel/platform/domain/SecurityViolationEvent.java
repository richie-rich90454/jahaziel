/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A security policy was violated.
 *
 * @param workspaceId the workspace
 * @param violationType the violation type
 * @param details structured detail
 * @param occurredAt when the event occurred
 */
public record SecurityViolationEvent(
    WorkspaceId workspaceId, String violationType, Map<String, String> details, Instant occurredAt)
    implements DomainEvent {
  public SecurityViolationEvent {
    Objects.requireNonNull(workspaceId, "workspaceId");
    if (violationType == null || violationType.isBlank()) {
      throw new IllegalArgumentException("violationType must not be blank");
    }
    Objects.requireNonNull(details, "details");
    Objects.requireNonNull(occurredAt, "occurredAt");
    details = Map.copyOf(details);
  }
}
