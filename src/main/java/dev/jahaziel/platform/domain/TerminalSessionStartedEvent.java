/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A terminal session started.
 *
 * @param sessionId the session
 * @param workspaceId the workspace
 * @param occurredAt when the event occurred
 */
public final class TerminalSessionStartedEvent extends DomainEvent {

    private final UUID sessionId;

    private final UUID workspaceId;

    private final Instant occurredAt;

    public TerminalSessionStartedEvent(UUID sessionId, UUID workspaceId, Instant occurredAt) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        this.sessionId = sessionId;
        this.workspaceId = workspaceId;
        this.occurredAt = occurredAt;
    }

    public UUID sessionId() {
        return this.sessionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID workspaceId() {
        return this.workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public Instant occurredAt() {
        return this.occurredAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TerminalSessionStartedEvent)) return false;
        TerminalSessionStartedEvent other = (TerminalSessionStartedEvent) o;
        return (java.util.Objects.equals(this.sessionId, other.sessionId))
                && java.util.Objects.equals(this.workspaceId, other.workspaceId)
                && java.util.Objects.equals(this.occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.sessionId, this.workspaceId, this.occurredAt);
    }

    @Override
    public String toString() {
        return "TerminalSessionStartedEvent["
                + "sessionId="
                + this.sessionId
                + ", "
                + "workspaceId="
                + this.workspaceId
                + ", "
                + "occurredAt="
                + this.occurredAt
                + "]";
    }
}
