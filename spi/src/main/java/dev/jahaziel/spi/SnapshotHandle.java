/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Opaque handle to a snapshot of a workspace home folder.
 *
 * @param snapshotId the snapshot identifier
 * @param snapshotPath absolute path to the read-only snapshot folder
 * @param createdAt when the snapshot was taken
 */
public record SnapshotHandle(UUID snapshotId, Path snapshotPath, Instant createdAt) {
  public SnapshotHandle {
    Objects.requireNonNull(snapshotId, "snapshotId");
    Objects.requireNonNull(snapshotPath, "snapshotPath");
    Objects.requireNonNull(createdAt, "createdAt");
  }
}
