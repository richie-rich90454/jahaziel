/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * A terminal session started.
 *
 * @param sessionId the session
 * @param workspaceId the workspace
 * @param occurredAt when the event occurred
 */
public record TerminalSessionStartedEvent(
    TerminalSessionId sessionId, WorkspaceId workspaceId, Instant occurredAt)
    implements DomainEvent {
  public TerminalSessionStartedEvent {
    Objects.requireNonNull(sessionId, "sessionId");
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
