/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import dev.jahaziel.networking.api.GatewayRouteService;
import dev.jahaziel.platform.api.JobContext;
import dev.jahaziel.platform.api.JobScope;
import dev.jahaziel.platform.api.PlatformJob;
import java.time.Duration;
import java.util.Objects;

/**
 * Converges the gateway routing table: starts, stops, and port changes all land here, so a missed
 * event costs at most one period.
 */
public final class GatewayRouteSyncJob implements PlatformJob {
  private final GatewayRouteService routes;

  /**
   * Creates the job.
   *
   * @param routes the route reconciler
   */
  public GatewayRouteSyncJob(GatewayRouteService routes) {
    this.routes = Objects.requireNonNull(routes, "routes");
  }

  @Override
  public void run(JobContext context) {
    routes.reconcile();
  }

  @Override
  public Duration period() {
    return Duration.ofSeconds(5);
  }

  @Override
  public JobScope scope() {
    return JobScope.CLUSTER;
  }
}
