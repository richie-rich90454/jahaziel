/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.internal;

import dev.jahaziel.monitoring.api.HourlyRollup;
import dev.jahaziel.monitoring.api.MetricsService;
import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.spi.PlatformJob;
import dev.jahaziel.workspace.api.WorkspaceService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Records hourly rollups. Live sampling from the agent Metrics feed replaces the provisional zeros;
 * one row per workspace per hour, skipped when already sampled this hour.
 */
public final class ResourceSamplingJob implements PlatformJob {
  private final MetricsService metrics;
  private final WorkspaceService workspaces;
  private final Clock clock;
  private LocalDateTime sampledHour;

  /**
   * Creates the job.
   *
   * @param metrics the metric rollups
   * @param workspaces the workspace lookup
   * @param clock the clock
   */
  public ResourceSamplingJob(MetricsService metrics, WorkspaceService workspaces, Clock clock) {
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public synchronized void run(JobContext context) {
    LocalDateTime hour =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
    if (hour.equals(sampledHour)) {
      return;
    }
    sampledHour = hour;
    for (var view : workspaces.describeAll()) {
      metrics.record(
          new HourlyRollup(
              view.workspace().id().value(),
              hour.toInstant(ZoneOffset.UTC),
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              0));
    }
  }

  @Override
  public Duration period() {
    return Duration.ofSeconds(10);
  }

  @Override
  public JobScope scope() {
    return JobScope.CLUSTER;
  }
}
