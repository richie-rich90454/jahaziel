/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One hourly resource rollup of a workspace.
 *
 * @param workspaceId the workspace
 * @param hourStart the hour start
 * @param cpuAvgPercent average CPU percent
 * @param cpuMaxPercent maximum CPU percent
 * @param memoryAvgMib average memory in MiB
 * @param memoryMaxMib maximum memory in MiB
 * @param diskReadAvgBps average disk read bytes per second
 * @param diskWriteAvgBps average disk write bytes per second
 * @param networkRxAvgBps average network receive bytes per second
 * @param networkTxAvgBps average network transmit bytes per second
 * @param processMax maximum process count
 */
public record HourlyRollup(
    UUID workspaceId,
    Instant hourStart,
    float cpuAvgPercent,
    float cpuMaxPercent,
    long memoryAvgMib,
    long memoryMaxMib,
    long diskReadAvgBps,
    long diskWriteAvgBps,
    long networkRxAvgBps,
    long networkTxAvgBps,
    int processMax) {
  public HourlyRollup {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(hourStart, "hourStart");
    if (cpuAvgPercent < 0 || cpuMaxPercent < 0) {
      throw new IllegalArgumentException("cpu values must not be negative");
    }
    if (memoryAvgMib < 0 || memoryMaxMib < 0) {
      throw new IllegalArgumentException("memory values must not be negative");
    }
    if (diskReadAvgBps < 0 || diskWriteAvgBps < 0) {
      throw new IllegalArgumentException("disk values must not be negative");
    }
    if (networkRxAvgBps < 0 || networkTxAvgBps < 0) {
      throw new IllegalArgumentException("network values must not be negative");
    }
    if (processMax < 0) {
      throw new IllegalArgumentException("processMax must not be negative");
    }
  }
}
