/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.internal;

import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import dev.jahaziel.spi.PlatformJob;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.workspace.api.WorkspaceService;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Daily automatic snapshots of every workspace. Platform-managed, off quota. */
public final class DailySnapshotJob implements PlatformJob {
  private static final DateTimeFormatter TAG =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm").withZone(ZoneOffset.UTC);

  private final dev.jahaziel.snapshot.api.SnapshotService snapshots;
  private final WorkspaceService workspaces;

  /**
   * Creates the job.
   *
   * @param snapshots the snapshot service
   * @param workspaces the workspace lookup
   */
  public DailySnapshotJob(
      dev.jahaziel.snapshot.api.SnapshotService snapshots, WorkspaceService workspaces) {
    this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
  }

  @Override
  public void run(JobContext context) {
    String tag = "auto-" + TAG.format(context.firedAt());
    RuntimeException first = null;
    for (var view : workspaces.describeAll()) {
      try {
        snapshots.create(view.workspace().id(), tag, SnapshotKind.AUTO, "system");
      } catch (RuntimeException failure) {
        if (first == null) {
          first = failure;
        }
      }
    }
    if (first != null) {
      throw first;
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
