/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier of a virtual machine.
 *
 * @param value the identifier value
 */
public final class VirtualMachineId {
    private final UUID value;

    public VirtualMachineId(UUID value) {
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
        if (o == null || getClass() != o.getClass()) return false;
        VirtualMachineId other = (VirtualMachineId) o;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(value);
        return result;
    }

    @Override
    public String toString() {
        return "VirtualMachineId{" + "value=" + value + '}';
    }
}
