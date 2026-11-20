/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A snapshot was created.
 *
 * @param workspaceId the workspace
 * @param snapshotId the snapshot
 * @param occurredAt when the event occurred
 */
public final class SnapshotCreatedEvent extends DomainEvent {

    private final UUID workspaceId;

    private final UUID snapshotId;

    private final Instant occurredAt;

    public SnapshotCreatedEvent(UUID workspaceId, UUID snapshotId, Instant occurredAt) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        this.workspaceId = workspaceId;
        this.snapshotId = snapshotId;
        this.occurredAt = occurredAt;
    }

    public UUID workspaceId() {
        return this.workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID snapshotId() {
        return this.snapshotId;
    }

    public UUID getSnapshotId() {
        return snapshotId;
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
        if (!(o instanceof SnapshotCreatedEvent)) return false;
        SnapshotCreatedEvent other = (SnapshotCreatedEvent) o;
        return (java.util.Objects.equals(this.workspaceId, other.workspaceId))
                && java.util.Objects.equals(this.snapshotId, other.snapshotId)
                && java.util.Objects.equals(this.occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.workspaceId, this.snapshotId, this.occurredAt);
    }

    @Override
    public String toString() {
        return "SnapshotCreatedEvent["
                + "workspaceId="
                + this.workspaceId
                + ", "
                + "snapshotId="
                + this.snapshotId
                + ", "
                + "occurredAt="
                + this.occurredAt
                + "]";
    }
}
