/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.internal;

import dev.jahaziel.monitoring.api.HourlyRollup;
import dev.jahaziel.monitoring.api.MetricsService;
import dev.jahaziel.monitoring.persistence.ResourceMetricRepository;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;

/** Database-backed metric rollups. */
public final class MetricsServiceImpl implements MetricsService {
  private final ResourceMetricRepository metrics;

  /**
   * Creates the service.
   *
   * @param metrics the metric rows
   */
  public MetricsServiceImpl(ResourceMetricRepository metrics) {
    this.metrics = Objects.requireNonNull(metrics, "metrics");
  }

  @Override
  public void record(HourlyRollup rollup) {
    Objects.requireNonNull(rollup, "rollup");
    metrics.save(MetricsMapper.toEntity(rollup));
  }

  @Override
  public Optional<HourlyRollup> latest(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    List<dev.jahaziel.monitoring.persistence.ResourceMetricEntity> rows =
        metrics.findLatest(workspaceId.value(), PageRequest.of(0, 1));
    return rows.stream().findFirst().map(MetricsMapper::toDomain);
  }
}
