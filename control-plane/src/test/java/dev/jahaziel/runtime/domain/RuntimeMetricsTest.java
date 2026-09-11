/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Behavior of {@link RuntimeMetrics}. */
class RuntimeMetricsTest {
  @Test
  void zeroHasNoUsage() {
    RuntimeMetrics metrics = RuntimeMetrics.zero();
    assertEquals(0, metrics.cpuUsagePercent());
    assertEquals(0, metrics.memoryUsedBytes());
    assertEquals(0, metrics.memoryTotalBytes());
    assertEquals(0, metrics.diskUsedBytes());
  }

  @Test
  void negativeValuesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new RuntimeMetrics(-1, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new RuntimeMetrics(0, -1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new RuntimeMetrics(0, 0, -1, 0));
    assertThrows(IllegalArgumentException.class, () -> new RuntimeMetrics(0, 0, 0, -1));
  }
}
