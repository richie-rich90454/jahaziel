/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import java.time.Instant;
import java.util.Objects;

/** Lifecycle state of a workspace. Impossible states are unrepresentable. */
public abstract class WorkspaceState {
    /** Requested, waiting for admission. */
    public static final class Requested extends WorkspaceState {
        private final Instant at;

        public Requested(Instant at) {
            this.at = at;
        }

        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Requested other = (Requested) o;
            return Objects.equals(at, other.at);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(at);
            return result;
        }

        @Override
        public String toString() {
            return "Requested{" + "at=" + at + '}';
        }
    }

    /** Storage and jail are being prepared. */
    public static final class Preparing extends WorkspaceState {
        private final Instant at;

        public Preparing(Instant at) {
            this.at = at;
        }

        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Preparing other = (Preparing) o;
            return Objects.equals(at, other.at);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(at);
            return result;
        }

        @Override
        public String toString() {
            return "Preparing{" + "at=" + at + '}';
        }
    }

    /** The virtual machine is booting. */
    public static final class Booting extends WorkspaceState {
        private final Instant at;

        public Booting(Instant at) {
            this.at = at;
        }

        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Booting other = (Booting) o;
            return Objects.equals(at, other.at);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(at);
            return result;
        }

        @Override
        public String toString() {
            return "Booting{" + "at=" + at + '}';
        }
    }

    /** Booted, waiting for the first user session. */
    public static final class Ready extends WorkspaceState {
        private final Instant at;

        public Ready(Instant at) {
            this.at = at;
        }

        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Ready other = (Ready) o;
            return Objects.equals(at, other.at);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(at);
            return result;
        }

        @Override
        public String toString() {
            return "Ready{" + "at=" + at + '}';
        }
    }

    /** Serving a user session. */
    public static final class Active extends WorkspaceState {
        private final Instant at;

        public Active(Instant at) {
            this.at = at;
        }

        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Active other = (Active) o;
            return Objects.equals(at, other.at);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(at);
            return result;
        }

        @Override
        public String toString() {
            return "Active{" + "at=" + at + '}';
        }
    }

    /** No input, socket, CPU or network activity for the idle window. */
    public static final class Idle extends WorkspaceState {
        private final Instant at;
        private final Instant since;

        public Idle(Instant at, Instant since) {
            this.at = at;
            this.since = since;
        }

        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        public Instant since() {
            return since;
        }

        public Instant getSince() {
            return since;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Idle other = (Idle) o;
            return Objects.equals(at, other.at) && Objects.equals(since, other.since);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(at);
            result = 31 * result + Objects.hashCode(since);
            return result;
        }

        @Override
        public String toString() {
            return "Idle{" + "at=" + at + ", since=" + since + '}';
        }
    }

    /** Stopped, home folder preserved. */
    public static final class Suspended extends WorkspaceState {
        private final Instant at;

        public Suspended(Instant at) {
            this.at = at;
        }

        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Suspended other = (Suspended) o;
            return Objects.equals(at, other.at);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(at);
            return result;
        }

        @Override
        public String toString() {
            return "Suspended{" + "at=" + at + '}';
        }
    }

    /** Removed by request. */
    public static final class Destroyed extends WorkspaceState {
        private final Instant at;

        public Destroyed(Instant at) {
            this.at = at;
        }

        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Destroyed other = (Destroyed) o;
            return Objects.equals(at, other.at);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(at);
            return result;
        }

        @Override
        public String toString() {
            return "Destroyed{" + "at=" + at + '}';
        }
    }

    /** Unrecoverable failure. */
    public static final class Failed extends WorkspaceState {
        private final Instant at;
        private final String reason;

        public Failed(Instant at, String reason) {
            this.at = at;
            this.reason = reason;
        }

        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        public String reason() {
            return reason;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Failed other = (Failed) o;
            return Objects.equals(at, other.at) && Objects.equals(reason, other.reason);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(at);
            result = 31 * result + Objects.hashCode(reason);
            return result;
        }

        @Override
        public String toString() {
            return "Failed{" + "at=" + at + ", reason=" + reason + '}';
        }
    }
}
