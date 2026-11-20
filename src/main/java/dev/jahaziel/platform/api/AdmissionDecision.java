/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.api;

/** Admission decision for a workspace start. Memory is reserved, never overcommitted. */
public abstract class AdmissionDecision {

    /**
     * Start approved.
     *
     * @param memoryMib the approved memory, possibly the soft floor
     */
    public static final class Admit extends AdmissionDecision {

        private final long memoryMib;

        public Admit(long memoryMib) {
            this.memoryMib = memoryMib;
        }

        public long memoryMib() {
            return this.memoryMib;
        }

        public long getMemoryMib() {
            return memoryMib;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Admit)) return false;
            Admit other = (Admit) o;
            return (this.memoryMib == other.memoryMib);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(this.memoryMib);
        }

        @Override
        public String toString() {
            return "Admit[" + "memoryMib=" + this.memoryMib + "]";
        }
    }

    /**
     * Start refused.
     *
     * @param reason why the start is refused
     */
    public static final class Refuse extends AdmissionDecision {

        private final String reason;

        public Refuse(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return this.reason;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Refuse)) return false;
            Refuse other = (Refuse) o;
            return (java.util.Objects.equals(this.reason, other.reason));
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(this.reason);
        }

        @Override
        public String toString() {
            return "Refuse[" + "reason=" + this.reason + "]";
        }
    }
}
