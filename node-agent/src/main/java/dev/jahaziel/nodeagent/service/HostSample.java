/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import java.util.Objects;

/**
 * One host resource sample for the control plane collector.
 *
 * @param cpuUsagePercent CPU usage percent
 * @param memoryUsedBytes memory used in bytes
 * @param memoryTotalBytes memory total in bytes
 * @param diskUsedBytes disk used in bytes
 * @param diskTotalBytes disk total in bytes
 * @param loadAverage1m one minute load average
 * @param loadAverage5m five minute load average
 * @param loadAverage15m fifteen minute load average
 */
public record HostSample(
    double cpuUsagePercent,
    long memoryUsedBytes,
    long memoryTotalBytes,
    long diskUsedBytes,
    long diskTotalBytes,
    double loadAverage1m,
    double loadAverage5m,
    double loadAverage15m) {
  public HostSample {
    if (cpuUsagePercent < 0) {
      throw new IllegalArgumentException("cpuUsagePercent must not be negative");
    }
    if (memoryUsedBytes < 0 || memoryTotalBytes < 0) {
      throw new IllegalArgumentException("memory values must not be negative");
    }
    if (diskUsedBytes < 0 || diskTotalBytes < 0) {
      throw new IllegalArgumentException("disk values must not be negative");
    }
    Objects.requireNonNull(cpuUsagePercent, "cpuUsagePercent");
  }
}
