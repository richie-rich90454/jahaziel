/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

/**
 * Latest resource sample of a node.
 *
 * @param cpuUsagePercent CPU usage percent
 * @param memoryUsedBytes memory used in bytes
 * @param memoryTotalBytes memory total in bytes
 * @param loadAverage1m one minute load average
 */
public record NodeMetrics(
    double cpuUsagePercent, long memoryUsedBytes, long memoryTotalBytes, double loadAverage1m) {
  public NodeMetrics {
    if (cpuUsagePercent < 0) {
      throw new IllegalArgumentException("cpuUsagePercent must not be negative");
    }
    if (memoryUsedBytes < 0) {
      throw new IllegalArgumentException("memoryUsedBytes must not be negative");
    }
    if (memoryTotalBytes < 0) {
      throw new IllegalArgumentException("memoryTotalBytes must not be negative");
    }
    if (loadAverage1m < 0) {
      throw new IllegalArgumentException("loadAverage1m must not be negative");
    }
  }

  /** Returns zero metrics, used before the first sample. */
  public static NodeMetrics zero() {
    return new NodeMetrics(0, 0, 0, 0);
  }
}
