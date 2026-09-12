/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.internal;

import dev.jahaziel.platform.api.JobContext;
import dev.jahaziel.platform.api.JobScope;
import dev.jahaziel.platform.api.PlatformJob;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.workspace.api.WorkspaceService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Enforces snapshot retention: rolling automatic snapshots, and pre-delete snapshots past their
 * age. Manual snapshots delete only on user request.
 */
public final class SnapshotRetentionJob implements PlatformJob {
  private final SnapshotService snapshots;
  private final WorkspaceService workspaces;
  private final int autoRetentionCount;
  private final int preDeleteRetentionDays;
  private final Clock clock;

  /**
   * Creates the job.
   *
   * @param snapshots the snapshot service
   * @param workspaces the workspace lookup
   * @param autoRetentionCount how many automatic snapshots survive per workspace
   * @param preDeleteRetentionDays how long pre-delete snapshots survive
   * @param clock the clock
   */
  public SnapshotRetentionJob(
      SnapshotService snapshots,
      WorkspaceService workspaces,
      int autoRetentionCount,
      int preDeleteRetentionDays,
      Clock clock) {
    this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    if (autoRetentionCount < 1) {
      throw new IllegalArgumentException("autoRetentionCount must be positive");
    }
    if (preDeleteRetentionDays < 1) {
      throw new IllegalArgumentException("preDeleteRetentionDays must be positive");
    }
    this.autoRetentionCount = autoRetentionCount;
    this.preDeleteRetentionDays = preDeleteRetentionDays;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public void run(JobContext context) {
    Instant cutoff = Instant.now(clock).minus(Duration.ofDays(preDeleteRetentionDays));
    for (var view : workspaces.describeAll()) {
      List<SnapshotMetadata> all = snapshots.list(view.workspace().id());
      List<SnapshotMetadata> autos =
          all.stream()
              .filter(meta -> meta.kind() == SnapshotKind.AUTO)
              .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
              .toList();
      for (int i = autoRetentionCount; i < autos.size(); i++) {
        snapshots.delete(autos.get(i).id());
      }
      for (SnapshotMetadata meta : all) {
        if (meta.kind() == SnapshotKind.PRE_DELETE && meta.createdAt().isBefore(cutoff)) {
          snapshots.delete(meta.id());
        }
      }
    }
  }

  @Override
  public Duration period() {
    return Duration.ofHours(24);
  }

  @Override
  public JobScope scope() {
    return JobScope.CLUSTER;
  }
}
