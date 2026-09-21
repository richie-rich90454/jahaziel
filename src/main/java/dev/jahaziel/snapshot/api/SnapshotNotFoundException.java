/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.api;

import dev.jahaziel.snapshot.domain.SnapshotId;

/** Thrown when a snapshot does not exist. */
public final class SnapshotNotFoundException extends RuntimeException {
  private final SnapshotId snapshotId;

  /**
   * Creates the exception.
   *
   * @param snapshotId the missing snapshot identifier
   */
  public SnapshotNotFoundException(SnapshotId snapshotId) {
    super("Snapshot " + snapshotId + " not found");
    this.snapshotId = snapshotId;
  }

  /** Returns the missing snapshot identifier. */
  public SnapshotId snapshotId() {
    return snapshotId;
  }
}
