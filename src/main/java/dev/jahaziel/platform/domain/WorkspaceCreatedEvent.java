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
public final class WorkspaceCreatedEvent extends DomainEvent {

    private final UUID workspaceId;

    private final UUID ownerId;

    private final Instant occurredAt;

    public WorkspaceCreatedEvent(UUID workspaceId, UUID ownerId, Instant occurredAt) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        this.workspaceId = workspaceId;
        this.ownerId = ownerId;
        this.occurredAt = occurredAt;
    }

    public UUID workspaceId() {
        return this.workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID ownerId() {
        return this.ownerId;
    }

    public UUID getOwnerId() {
        return ownerId;
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
        if (!(o instanceof WorkspaceCreatedEvent)) return false;
        WorkspaceCreatedEvent other = (WorkspaceCreatedEvent) o;
        return (java.util.Objects.equals(this.workspaceId, other.workspaceId))
                && java.util.Objects.equals(this.ownerId, other.ownerId)
                && java.util.Objects.equals(this.occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.workspaceId, this.ownerId, this.occurredAt);
    }

    @Override
    public String toString() {
        return "WorkspaceCreatedEvent["
                + "workspaceId="
                + this.workspaceId
                + ", "
                + "ownerId="
                + this.ownerId
                + ", "
                + "occurredAt="
                + this.occurredAt
                + "]";
    }
}
