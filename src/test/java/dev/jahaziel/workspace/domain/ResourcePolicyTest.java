/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Behavior of {@link ResourcePolicy}. */
class ResourcePolicyTest {
  @Test
  void defaultsMatchVmDefaults() {
    ResourcePolicy policy = ResourcePolicy.defaults();
    assertEquals(2048, policy.maxMemoryMib());
    assertEquals(2, policy.maxVcpuCount());
    assertEquals(20, policy.maxDiskGiB());
  }

  @Test
  void nonPositiveCeilingsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new ResourcePolicy(0, 2, 20));
    assertThrows(IllegalArgumentException.class, () -> new ResourcePolicy(2048, 0, 20));
    assertThrows(IllegalArgumentException.class, () -> new ResourcePolicy(2048, 2, 0));
  }
}
