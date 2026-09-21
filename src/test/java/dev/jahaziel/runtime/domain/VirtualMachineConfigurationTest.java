/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Behavior of {@link VirtualMachineConfiguration}. */
class VirtualMachineConfigurationTest {
  @Test
  void storesSizing() {
    VirtualMachineConfiguration config = new VirtualMachineConfiguration(2, 2048);
    assertEquals(2, config.vcpuCount());
    assertEquals(2048, config.memoryMib());
  }

  @Test
  void nonPositiveSizingIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new VirtualMachineConfiguration(0, 2048));
    assertThrows(IllegalArgumentException.class, () -> new VirtualMachineConfiguration(2, 0));
  }
}
