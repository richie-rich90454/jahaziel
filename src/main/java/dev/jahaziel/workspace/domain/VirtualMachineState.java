/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import java.time.Instant;
import java.util.Objects;

/** Runtime state of a virtual machine. */
public abstract class VirtualMachineState {
    /** Running on a host. */
    public static final class Running extends VirtualMachineState {
        private final Instant at;
        private final String hostname;

        public Running(Instant at, String hostname) {
            Objects.requireNonNull(at, "at");
            if (hostname == null || hostname.isBlank()) {
                throw new IllegalArgumentException("hostname must not be blank");
            }

            this.at = at;
            this.hostname = hostname;
        }

        public Instant at() {
            return at;
        }

        public Instant getAt() {
            return at;
        }

        public String hostname() {
            return hostname;
        }

        public String getHostname() {
            return hostname;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Running other = (Running) o;
            return Objects.equals(at, other.at) && Objects.equals(hostname, other.hostname);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(at);
            result = 31 * result + Objects.hashCode(hostname);
            return result;
        }

        @Override
        public String toString() {
            return "Running{" + "at=" + at + ", hostname=" + hostname + '}';
        }
    }

    /** Stopped, no guest running. */
    public static final class Stopped extends VirtualMachineState {
        private final Instant at;

        public Stopped(Instant at) {
            Objects.requireNonNull(at, "at");

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
            Stopped other = (Stopped) o;
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
            return "Stopped{" + "at=" + at + '}';
        }
    }

    /** Crashed while running. */
    public static final class Crashed extends VirtualMachineState {
        private final Instant at;
        private final String reason;

        public Crashed(Instant at, String reason) {
            Objects.requireNonNull(at, "at");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }

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
            Crashed other = (Crashed) o;
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
            return "Crashed{" + "at=" + at + ", reason=" + reason + '}';
        }
    }
}
