/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.workspace.domain.VirtualMachineId;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Behavior of {@link VirtualMachineId}. */
class VirtualMachineIdTest {
    @Test
    void equalValuesAreEqual() {
        UUID value = UUID.randomUUID();
        assertEquals(new VirtualMachineId(value), new VirtualMachineId(value));
    }

    @Test
    void differentValuesAreNotEqual() {
        assertNotEquals(
                new VirtualMachineId(UUID.randomUUID()), new VirtualMachineId(UUID.randomUUID()));
    }

    @Test
    void nullValueIsRejected() {
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        new VirtualMachineId(null);
                    }
                });
    }
}
