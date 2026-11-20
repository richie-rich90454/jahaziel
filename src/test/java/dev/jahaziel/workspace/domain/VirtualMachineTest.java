/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Behavior of {@link VirtualMachine}. */
class VirtualMachineTest {
    private VirtualMachineId machineId;
    private WorkspaceId workspaceId;
    private VirtualMachine machine;

    @BeforeEach
    void setUp() {
        machineId = new VirtualMachineId(UUID.randomUUID());
        workspaceId = new WorkspaceId(UUID.randomUUID());
        machine =
                new VirtualMachine(
                        machineId, workspaceId, new VirtualMachineConfiguration(2, 2048));
    }

    @Test
    void startsStoppedWithZeroMetrics() {
        assertInstanceOf(VirtualMachineState.Stopped.class, machine.state());
        assertEquals(RuntimeMetrics.zero(), machine.metrics());
    }

    @Test
    void bootsRunsAndStops() {
        machine.boot("vm-1");
        VirtualMachineState.Running running =
                assertInstanceOf(VirtualMachineState.Running.class, machine.state());
        assertEquals("vm-1", running.hostname());
        machine.stop();
        assertInstanceOf(VirtualMachineState.Stopped.class, machine.state());
    }

    @Test
    void bootRequiresStopped() {
        machine.boot("vm-1");
        assertThrows(
                IllegalStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        machine.boot("vm-1");
                    }
                });
    }

    @Test
    void crashRequiresRunning() {
        assertThrows(
                IllegalStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        machine.crash("panic");
                    }
                });
        machine.boot("vm-1");
        machine.crash("panic");
        VirtualMachineState.Crashed crashed =
                assertInstanceOf(VirtualMachineState.Crashed.class, machine.state());
        assertEquals("panic", crashed.reason());
    }

    @Test
    void stopRequiresRunning() {
        assertThrows(
                IllegalStateException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        machine.stop();
                    }
                });
    }

    @Test
    void blankHostnamesAndReasonsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        machine.boot("  ");
                    }
                });
        machine.boot("vm-1");
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        machine.crash("  ");
                    }
                });
    }

    @Test
    void metricsUpdate() {
        RuntimeMetrics sample = new RuntimeMetrics(42, 1024, 2048, 512);
        machine.updateMetrics(sample);
        assertEquals(sample, machine.metrics());
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        machine.updateMetrics(null);
                    }
                });
    }
}
