/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Human tag of a snapshot, unique per workspace. */
public final class SnapshotTag {

    private final String value;

    public SnapshotTag(String value) {
        // absolute tags; no separate isAbsolute check is needed.
        if (value == null
                || value.isBlank()
                || value.contains("..")
                || value.contains("/")
                || value.contains("\\")
                || value.startsWith(".")) {
            throw new IllegalArgumentException("invalid snapshot tag: " + value);
        }
        this.value = value;
    }

    public String value() {
        return this.value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SnapshotTag)) return false;
        SnapshotTag other = (SnapshotTag) o;
        return (java.util.Objects.equals(this.value, other.value));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.value);
    }

    @Override
    public String toString() {
        return "SnapshotTag[" + "value=" + this.value + "]";
    }
}
