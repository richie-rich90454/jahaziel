/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A policy changed.
 *
 * @param scope the policy scope, global, user or workspace
 * @param scopeId the scoped identifier, empty for global scope
 * @param occurredAt when the event occurred
 */
public final class PolicyChangedEvent extends DomainEvent {

    private final String scope;

    private final String scopeId;

    private final Instant occurredAt;

    public PolicyChangedEvent(String scope, String scopeId, Instant occurredAt) {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        this.scope = scope;
        this.scopeId = scopeId;
        this.occurredAt = occurredAt;
    }

    public String scope() {
        return this.scope;
    }

    public String getScope() {
        return scope;
    }

    public String scopeId() {
        return this.scopeId;
    }

    public String getScopeId() {
        return scopeId;
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
        if (!(o instanceof PolicyChangedEvent)) return false;
        PolicyChangedEvent other = (PolicyChangedEvent) o;
        return (java.util.Objects.equals(this.scope, other.scope))
                && java.util.Objects.equals(this.scopeId, other.scopeId)
                && java.util.Objects.equals(this.occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.scope, this.scopeId, this.occurredAt);
    }

    @Override
    public String toString() {
        return "PolicyChangedEvent["
                + "scope="
                + this.scope
                + ", "
                + "scopeId="
                + this.scopeId
                + ", "
                + "occurredAt="
                + this.occurredAt
                + "]";
    }
}
