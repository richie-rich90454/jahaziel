/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier of a user.
 *
 * @param value the identifier value
 */
public final class UserId {
    private final UUID value;

    /** Creates an identifier. */
    public UserId(UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /** Returns the identifier value. */
    public UUID value() {
        return value;
    }

    public UUID getValue() {
        return value;
    }

    /** Compares by class and value. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(value, ((UserId) o).value);
    }

    /** Returns the hash code derived from the value. */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /** Returns a readable representation. */
    @Override
    public String toString() {
        return "UserId[value=" + value + "]";
    }
}
