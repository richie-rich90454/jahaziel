/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Behavior of {@link NodeMetrics}. */
class NodeMetricsTest {
  @Test
  void zeroHasNoUsage() {
    NodeMetrics metrics = NodeMetrics.zero();
    assertEquals(0, metrics.cpuUsagePercent());
    assertEquals(0, metrics.memoryUsedBytes());
    assertEquals(0, metrics.memoryTotalBytes());
    assertEquals(0, metrics.loadAverage1m());
  }

  @Test
  void negativeValuesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new NodeMetrics(-1, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new NodeMetrics(0, -1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new NodeMetrics(0, 0, -1, 0));
    assertThrows(IllegalArgumentException.class, () -> new NodeMetrics(0, 0, 0, -1));
  }
}
