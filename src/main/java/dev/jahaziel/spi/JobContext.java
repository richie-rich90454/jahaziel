/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.time.Instant;
import java.util.Objects;

/**
 * Context a scheduled job runs with.
 *
 * @param firedAt when the run fired
 */
public final class JobContext {

    private final Instant firedAt;

    public JobContext(Instant firedAt) {
        Objects.requireNonNull(firedAt, "firedAt");
        this.firedAt = firedAt;
    }

    /** Returns a context for right now. */
    public static JobContext now() {
        return new JobContext(Instant.now());
    }

    public Instant firedAt() {
        return this.firedAt;
    }

    public Instant getFiredAt() {
        return firedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JobContext)) return false;
        JobContext other = (JobContext) o;
        return (java.util.Objects.equals(this.firedAt, other.firedAt));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.firedAt);
    }

    @Override
    public String toString() {
        return "JobContext[" + "firedAt=" + this.firedAt + "]";
    }
}
