/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.domain;

/**
 * Latest resource sample of a virtual machine.
 *
 * @param cpuUsagePercent CPU usage percent
 * @param memoryUsedBytes guest memory used in bytes
 * @param memoryTotalBytes guest memory total in bytes
 * @param diskUsedBytes disk used in bytes
 */
public record RuntimeMetrics(
    double cpuUsagePercent, long memoryUsedBytes, long memoryTotalBytes, long diskUsedBytes) {
  public RuntimeMetrics {
    if (cpuUsagePercent < 0) {
      throw new IllegalArgumentException("cpuUsagePercent must not be negative");
    }
    if (memoryUsedBytes < 0) {
      throw new IllegalArgumentException("memoryUsedBytes must not be negative");
    }
    if (memoryTotalBytes < 0) {
      throw new IllegalArgumentException("memoryTotalBytes must not be negative");
    }
    if (diskUsedBytes < 0) {
      throw new IllegalArgumentException("diskUsedBytes must not be negative");
    }
  }

  /** Returns zero metrics, used before the first sample. */
  public static RuntimeMetrics zero() {
    return new RuntimeMetrics(0, 0, 0, 0);
  }
}
