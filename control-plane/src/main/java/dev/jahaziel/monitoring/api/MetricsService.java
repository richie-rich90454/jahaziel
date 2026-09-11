/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.api;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.Optional;

/** Hourly resource rollups. Live samples arrive from the node agent. */
public interface MetricsService {
  /**
   * Records one hourly rollup.
   *
   * @param rollup the rollup
   */
  void record(HourlyRollup rollup);

  /**
   * Returns the newest rollup of a workspace.
   *
   * @param workspaceId the workspace
   * @return the rollup, empty when none recorded
   */
  Optional<HourlyRollup> latest(WorkspaceId workspaceId);
}
