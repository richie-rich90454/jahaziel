/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque handle to a running workspace runtime.
 *
 * @param value the runtime identifier
 */
public final class RuntimeHandle {

    private final UUID value;

    public RuntimeHandle(UUID value) {
        Objects.requireNonNull(value, "value");
        this.value = value;
    }

    public UUID value() {
        return this.value;
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeHandle)) return false;
        RuntimeHandle other = (RuntimeHandle) o;
        return (java.util.Objects.equals(this.value, other.value));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.value);
    }

    @Override
    public String toString() {
        return "RuntimeHandle[" + "value=" + this.value + "]";
    }
}
