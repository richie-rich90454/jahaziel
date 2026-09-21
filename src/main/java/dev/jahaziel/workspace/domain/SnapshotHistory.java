/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import dev.jahaziel.snapshot.domain.SnapshotId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ordered history of snapshot identifiers of one workspace. */
public final class SnapshotHistory {
  private final List<SnapshotId> snapshotIds = new ArrayList<>();

  /**
   * Records one snapshot.
   *
   * @param snapshotId the snapshot identifier
   */
  public void record(SnapshotId snapshotId) {
    snapshotIds.add(Objects.requireNonNull(snapshotId, "snapshotId"));
  }

  /**
   * Returns the recorded snapshots in order.
   *
   * @return a defensive copy
   */
  public List<SnapshotId> snapshots() {
    return List.copyOf(snapshotIds);
  }
}
