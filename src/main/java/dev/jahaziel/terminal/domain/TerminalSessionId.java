/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier of a terminal session. Component: value the identifier value. */
public final class TerminalSessionId {
    private final UUID value;

    public TerminalSessionId(UUID value) {
        Objects.requireNonNull(value, "value");
        this.value = value;
    }

    public UUID value() {
        return value;
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TerminalSessionId)) return false;
        TerminalSessionId other = (TerminalSessionId) o;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "TerminalSessionId[value=" + value + "]";
    }
}
