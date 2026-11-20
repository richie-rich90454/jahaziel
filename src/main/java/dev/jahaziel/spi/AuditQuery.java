/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Criteria for querying the audit store.
 *
 * @param from earliest timestamp, empty for no lower bound
 * @param to latest timestamp, empty for no upper bound
 * @param actor actor filter, empty for all actors
 * @param action action filter, empty for all actions
 * @param limit maximum events to return
 */
public final class AuditQuery {

    private final Optional<Instant> from;

    private final Optional<Instant> to;

    private final Optional<String> actor;

    private final Optional<String> action;

    private final int limit;

    public AuditQuery(
            Optional<Instant> from,
            Optional<Instant> to,
            Optional<String> actor,
            Optional<String> action,
            int limit) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.from = from;
        this.to = to;
        this.actor = actor;
        this.action = action;
        this.limit = limit;
    }

    public Optional<Instant> from() {
        return this.from;
    }

    public Optional<Instant> getFrom() {
        return from;
    }

    public Optional<Instant> to() {
        return this.to;
    }

    public Optional<Instant> getTo() {
        return to;
    }

    public Optional<String> actor() {
        return this.actor;
    }

    public Optional<String> getActor() {
        return actor;
    }

    public Optional<String> action() {
        return this.action;
    }

    public Optional<String> getAction() {
        return action;
    }

    public int limit() {
        return this.limit;
    }

    public int getLimit() {
        return limit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditQuery)) return false;
        AuditQuery other = (AuditQuery) o;
        return (java.util.Objects.equals(this.from, other.from))
                && java.util.Objects.equals(this.to, other.to)
                && java.util.Objects.equals(this.actor, other.actor)
                && java.util.Objects.equals(this.action, other.action)
                && this.limit == other.limit;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.from, this.to, this.actor, this.action, this.limit);
    }

    @Override
    public String toString() {
        return "AuditQuery["
                + "from="
                + this.from
                + ", "
                + "to="
                + this.to
                + ", "
                + "actor="
                + this.actor
                + ", "
                + "action="
                + this.action
                + ", "
                + "limit="
                + this.limit
                + "]";
    }
}
