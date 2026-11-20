/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import java.util.Objects;

/**
 * Stable identifier of a node.
 *
 * @param value the identifier value
 */
public final class NodeId {
    private final String value;

    /**
     * Creates an identifier.
     *
     * @param value the identifier value
     */
    public NodeId(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        this.value = value;
    }

    /** Returns the identifier value. */
    public String value() {
        return value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return value.equals(((NodeId) other).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "NodeId[value=" + value + "]";
    }
}
