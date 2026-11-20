/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier of a snapshot.
 *
 * @param value the identifier value
 */
public final class SnapshotId {
    private final UUID value;

    /**
     * Creates the identifier.
     *
     * @param value the identifier value
     */
    public SnapshotId(UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /** Returns the identifier value. */
    public UUID value() {
        return value;
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SnapshotId)) {
            return false;
        }
        return value.equals(((SnapshotId) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "SnapshotId[value=" + value + "]";
    }
}
