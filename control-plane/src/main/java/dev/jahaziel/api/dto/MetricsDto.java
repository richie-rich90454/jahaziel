/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.monitoring.api.HourlyRollup;

/**
 * Current metrics of a workspace for reads. Zeros until the sampler feeds live values.
 *
 * @param cpuUsagePercent CPU usage percent
 * @param memoryUsedBytes guest memory used in bytes
 * @param memoryTotalBytes guest memory total in bytes
 * @param diskUsedBytes disk used in bytes
 * @param networkRxBytesPerSec network receive bytes per second
 * @param networkTxBytesPerSec network transmit bytes per second
 * @param processCount process count
 */
public record MetricsDto(
    double cpuUsagePercent,
    long memoryUsedBytes,
    long memoryTotalBytes,
    long diskUsedBytes,
    long networkRxBytesPerSec,
    long networkTxBytesPerSec,
    int processCount) {
  /**
   * Maps a rollup to a DTO.
   *
   * @param rollup the rollup
   * @return the DTO
   */
  public static MetricsDto from(HourlyRollup rollup) {
    return new MetricsDto(
        rollup.cpuAvgPercent(),
        rollup.memoryAvgMib() * 1024 * 1024,
        rollup.memoryMaxMib() * 1024 * 1024,
        rollup.diskReadAvgBps(),
        rollup.networkRxAvgBps(),
        rollup.networkTxAvgBps(),
        rollup.processMax());
  }

  /**
   * Returns zero metrics. Served when no rollup exists yet.
   *
   * @return the zero DTO
   */
  public static MetricsDto zero() {
    return new MetricsDto(0, 0, 0, 0, 0, 0, 0);
  }
}
