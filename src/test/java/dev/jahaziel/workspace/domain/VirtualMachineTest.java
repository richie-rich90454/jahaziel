/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.terminal.domain.TerminalSession;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link VirtualMachine}. */
class VirtualMachineTest {
  private VirtualMachineId machineId;
  private WorkspaceId workspaceId;
  private VirtualMachine machine;

  @BeforeEach
  void setUp() {
    machineId = new VirtualMachineId(UUID.randomUUID());
    workspaceId = new WorkspaceId(UUID.randomUUID());
    machine = new VirtualMachine(machineId, workspaceId, new VirtualMachineConfiguration(2, 2048));
  }

  @Test
  void startsStoppedWithZeroMetrics() {
    assertInstanceOf(VirtualMachineState.Stopped.class, machine.state());
    assertEquals(RuntimeMetrics.zero(), machine.metrics());
    assertTrue(machine.terminals().isEmpty());
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
    assertThrows(IllegalStateException.class, () -> machine.boot("vm-1"));
  }

  @Test
  void crashRequiresRunning() {
    assertThrows(IllegalStateException.class, () -> machine.crash("panic"));
    machine.boot("vm-1");
    machine.crash("panic");
    VirtualMachineState.Crashed crashed =
        assertInstanceOf(VirtualMachineState.Crashed.class, machine.state());
    assertEquals("panic", crashed.reason());
  }

  @Test
  void stopRequiresRunning() {
    assertThrows(IllegalStateException.class, machine::stop);
  }

  @Test
  void blankHostnamesAndReasonsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> machine.boot("  "));
    machine.boot("vm-1");
    assertThrows(IllegalArgumentException.class, () -> machine.crash("  "));
  }

  @Test
  void terminalsAttachAndDetach() {
    machine.boot("vm-1");
    TerminalSession session = newSession();
    machine.attachTerminal(session);
    assertEquals(1, machine.terminals().size());
    machine.detachTerminal(session.id());
    assertTrue(machine.terminals().isEmpty());
  }

  @Test
  void attachRequiresRunning() {
    assertThrows(IllegalStateException.class, () -> machine.attachTerminal(newSession()));
  }

  @Test
  void detachUnknownSessionThrows() {
    machine.boot("vm-1");
    assertThrows(
        IllegalArgumentException.class,
        () -> machine.detachTerminal(new TerminalSessionId(UUID.randomUUID())));
  }

  @Test
  void metricsUpdate() {
    RuntimeMetrics sample = new RuntimeMetrics(42, 1024, 2048, 512);
    machine.updateMetrics(sample);
    assertEquals(sample, machine.metrics());
    assertThrows(NullPointerException.class, () -> machine.updateMetrics(null));
  }

  private TerminalSession newSession() {
    return new TerminalSession(
        new TerminalSessionId(UUID.randomUUID()),
        workspaceId,
        new UserId(UUID.randomUUID()),
        30,
        120);
  }
}
