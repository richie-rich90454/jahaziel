/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.internal;

import dev.jahaziel.platform.api.JobContext;
import dev.jahaziel.platform.api.JobScope;
import dev.jahaziel.platform.api.PlatformJob;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.time.Duration;
import java.util.Objects;

/**
 * Suspends workspaces that already reached idle. Full idle detection (terminal input, socket
 * activity, CPU, network over the idle window) lands with the metrics feed; until then the job
 * parks idle workspaces to make room, never killing running ones.
 */
public final class VmIdleCheckJob implements PlatformJob {
  private final WorkspaceService workspaces;
  private final RuntimeService runtime;

  /**
   * Creates the job.
   *
   * @param workspaces the workspace lookup
   * @param runtime the runtime lifecycle
   */
  public VmIdleCheckJob(WorkspaceService workspaces, RuntimeService runtime) {
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  @Override
  public void run(JobContext context) {
    for (var view : workspaces.describeAll()) {
      if (view.workspace().state() instanceof WorkspaceState.Idle) {
        runtime.stop(view.workspace().id());
      }
    }
  }

  @Override
  public Duration period() {
    return Duration.ofMinutes(1);
  }

  @Override
  public JobScope scope() {
    return JobScope.CLUSTER;
  }
}
