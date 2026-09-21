/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Behavior of {@link NodeCapacity}. */
class NodeCapacityTest {
  @Test
  void storesCapacity() {
    NodeCapacity capacity = new NodeCapacity(14, 49152, 500);
    assertEquals(14, capacity.cpuCores());
    assertEquals(49152, capacity.memoryMib());
    assertEquals(500, capacity.diskGib());
  }

  @Test
  void nonPositiveCapacityIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new NodeCapacity(0, 49152, 500));
    assertThrows(IllegalArgumentException.class, () -> new NodeCapacity(14, 0, 500));
    assertThrows(IllegalArgumentException.class, () -> new NodeCapacity(14, 49152, 0));
  }
}
