/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.internal;

import dev.jahaziel.node.api.NodeService;
import dev.jahaziel.platform.api.JobContext;
import dev.jahaziel.platform.api.JobScope;
import dev.jahaziel.platform.api.PlatformJob;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Marks nodes unhealthy when their heartbeat goes stale. Heartbeats arrive from the agent
 * poller; without a fresh heartbeat for the stale window the node stops receiving placements.
 */
public final class HealthCheckJob implements PlatformJob {
  private final NodeService nodes;
  private final Duration staleAfter;
  private final Clock clock;

  /**
   * Creates the job.
   *
   * @param nodes the node registry
   * @param staleAfter how old a heartbeat may be
   * @param clock the clock
   */
  public HealthCheckJob(NodeService nodes, Duration staleAfter, Clock clock) {
    this.nodes = Objects.requireNonNull(nodes, "nodes");
    this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public void run(JobContext context) {
    Instant cutoff = Instant.now(clock).minus(staleAfter);
    for (var node : nodes.list()) {
      boolean stale =
          node.lastHeartbeat().map(seen -> seen.isBefore(cutoff)).orElse(true);
      if (stale) {
        try {
          nodes.markUnhealthy(node.id(), "no heartbeat for " + staleAfter.getSeconds() + "s");
        } catch (IllegalStateException already) {
          // Already unhealthy or draining; nothing to do.
        }
      }
    }
  }

  @Override
  public Duration period() {
    return Duration.ofSeconds(30);
  }

  @Override
  public JobScope scope() {
    return JobScope.CLUSTER;
  }
}
