/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A security policy was violated.
 *
 * @param workspaceId the workspace
 * @param violationType the violation type
 * @param details structured detail
 * @param occurredAt when the event occurred
 */
public final class SecurityViolationEvent extends DomainEvent {

    private final UUID workspaceId;

    private final String violationType;

    private final Map<String, String> details;

    private final Instant occurredAt;

    public SecurityViolationEvent(
            UUID workspaceId,
            String violationType,
            Map<String, String> details,
            Instant occurredAt) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (violationType == null || violationType.isBlank()) {
            throw new IllegalArgumentException("violationType must not be blank");
        }
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(occurredAt, "occurredAt");
        details = Map.copyOf(details);
        this.workspaceId = workspaceId;
        this.violationType = violationType;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public UUID workspaceId() {
        return this.workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String violationType() {
        return this.violationType;
    }

    public String getViolationType() {
        return violationType;
    }

    public Map<String, String> details() {
        return this.details;
    }

    public Map<String, String> getDetails() {
        return details;
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
        if (!(o instanceof SecurityViolationEvent)) return false;
        SecurityViolationEvent other = (SecurityViolationEvent) o;
        return (java.util.Objects.equals(this.workspaceId, other.workspaceId))
                && java.util.Objects.equals(this.violationType, other.violationType)
                && java.util.Objects.equals(this.details, other.details)
                && java.util.Objects.equals(this.occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.workspaceId, this.violationType, this.details, this.occurredAt);
    }

    @Override
    public String toString() {
        return "SecurityViolationEvent["
                + "workspaceId="
                + this.workspaceId
                + ", "
                + "violationType="
                + this.violationType
                + ", "
                + "details="
                + this.details
                + ", "
                + "occurredAt="
                + this.occurredAt
                + "]";
    }
}
