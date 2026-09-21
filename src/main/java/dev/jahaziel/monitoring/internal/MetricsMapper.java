/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.internal;

import dev.jahaziel.monitoring.api.HourlyRollup;
import dev.jahaziel.monitoring.persistence.ResourceMetricEntity;
import java.util.UUID;

/** Maps metric rows to rollups and back. Pure static functions. */
public final class MetricsMapper {
  private MetricsMapper() {}

  /**
   * Maps a rollup to its row.
   *
   * @param rollup the rollup
   * @return the row
   */
  public static ResourceMetricEntity toEntity(HourlyRollup rollup) {
    ResourceMetricEntity row = new ResourceMetricEntity();
    ResourceMetricEntity.Id id = new ResourceMetricEntity.Id();
    id.setWorkspaceId(rollup.workspaceId());
    id.setHourStart(rollup.hourStart());
    row.setId(id);
    row.setCpuAvgPercent(rollup.cpuAvgPercent());
    row.setCpuMaxPercent(rollup.cpuMaxPercent());
    row.setMemoryAvgMib(rollup.memoryAvgMib());
    row.setMemoryMaxMib(rollup.memoryMaxMib());
    row.setDiskReadAvgBps(rollup.diskReadAvgBps());
    row.setDiskWriteAvgBps(rollup.diskWriteAvgBps());
    row.setNetworkRxAvgBps(rollup.networkRxAvgBps());
    row.setNetworkTxAvgBps(rollup.networkTxAvgBps());
    row.setProcessMax(rollup.processMax());
    return row;
  }

  /**
   * Rebuilds a rollup from its row.
   *
   * @param row the row
   * @return the rollup
   */
  public static HourlyRollup toDomain(ResourceMetricEntity row) {
    UUID workspace = row.getId().getWorkspaceId();
    return new HourlyRollup(
        workspace,
        row.getId().getHourStart(),
        row.getCpuAvgPercent(),
        row.getCpuMaxPercent(),
        row.getMemoryAvgMib(),
        row.getMemoryMaxMib(),
        row.getDiskReadAvgBps(),
        row.getDiskWriteAvgBps(),
        row.getNetworkRxAvgBps(),
        row.getNetworkTxAvgBps(),
        row.getProcessMax());
  }
}
