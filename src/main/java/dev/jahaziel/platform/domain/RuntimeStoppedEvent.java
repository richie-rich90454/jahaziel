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
public final class RuntimeStoppedEvent extends DomainEvent {

    private final UUID workspaceId;

    private final Instant occurredAt;

    public RuntimeStoppedEvent(UUID workspaceId, Instant occurredAt) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        this.workspaceId = workspaceId;
        this.occurredAt = occurredAt;
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
        if (!(o instanceof RuntimeStoppedEvent)) return false;
        RuntimeStoppedEvent other = (RuntimeStoppedEvent) o;
        return (java.util.Objects.equals(this.workspaceId, other.workspaceId))
                && java.util.Objects.equals(this.occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.workspaceId, this.occurredAt);
    }

    @Override
    public String toString() {
        return "RuntimeStoppedEvent["
                + "workspaceId="
                + this.workspaceId
                + ", "
                + "occurredAt="
                + this.occurredAt
                + "]";
    }
}
