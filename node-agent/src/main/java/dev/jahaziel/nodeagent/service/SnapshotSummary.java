/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import dev.jahaziel.spi.SnapshotHandle;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A listed snapshot.
 *
 * @param handle the snapshot handle
 * @param tag the human tag
 * @param createdAt when the snapshot was taken
 */
public record SnapshotSummary(SnapshotHandle handle, String tag, Instant createdAt) {
  public SnapshotSummary {
    Objects.requireNonNull(handle, "handle");
    if (tag == null || tag.isBlank()) {
      throw new IllegalArgumentException("tag must not be blank");
    }
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /** Returns the snapshot subvolume path. */
  public Path subvolumePath() {
    return handle.subvolumePath();
  }

  /** Returns the snapshot identifier. */
  public UUID snapshotId() {
    return handle.snapshotId();
  }
}
