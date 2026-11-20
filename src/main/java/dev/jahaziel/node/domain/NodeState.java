/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import java.time.Instant;
import java.util.Objects;

/** Health state of a node. */
public abstract class NodeState {
    /** Healthy and accepting placements. */
    public static final class Healthy extends NodeState {
        private final Instant at;

        /** Creates the state. */
        public Healthy(Instant at) {
            Objects.requireNonNull(at, "at");
            this.at = at;
        }

        /** Returns when the state was entered. */
        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            return at.equals(((Healthy) other).at);
        }

        @Override
        public int hashCode() {
            return Objects.hash(at);
        }

        @Override
        public String toString() {
            return "Healthy[at=" + at + "]";
        }
    }

    /** Degraded but accepting placements. */
    public static final class Degraded extends NodeState {
        private final Instant at;
        private final String reason;

        /** Creates the state. */
        public Degraded(Instant at, String reason) {
            Objects.requireNonNull(at, "at");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
            this.at = at;
            this.reason = reason;
        }

        /** Returns when the state was entered. */
        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        /** Returns the degradation reason. */
        public String reason() {
            return reason;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            Degraded that = (Degraded) other;
            return at.equals(that.at) && reason.equals(that.reason);
        }

        @Override
        public int hashCode() {
            return Objects.hash(at, reason);
        }

        @Override
        public String toString() {
            return "Degraded[at=" + at + ", reason=" + reason + "]";
        }
    }

    /** Draining, no new placements. */
    public static final class Draining extends NodeState {
        private final Instant at;

        /** Creates the state. */
        public Draining(Instant at) {
            Objects.requireNonNull(at, "at");
            this.at = at;
        }

        /** Returns when the state was entered. */
        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            return at.equals(((Draining) other).at);
        }

        @Override
        public int hashCode() {
            return Objects.hash(at);
        }

        @Override
        public String toString() {
            return "Draining[at=" + at + "]";
        }
    }

    /** Unhealthy, no placements. */
    public static final class Unhealthy extends NodeState {
        private final Instant at;
        private final String reason;

        /** Creates the state. */
        public Unhealthy(Instant at, String reason) {
            Objects.requireNonNull(at, "at");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
            this.at = at;
            this.reason = reason;
        }

        /** Returns when the state was entered. */
        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        /** Returns why the node is unhealthy. */
        public String reason() {
            return reason;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            Unhealthy that = (Unhealthy) other;
            return at.equals(that.at) && reason.equals(that.reason);
        }

        @Override
        public int hashCode() {
            return Objects.hash(at, reason);
        }

        @Override
        public String toString() {
            return "Unhealthy[at=" + at + ", reason=" + reason + "]";
        }
    }
}
