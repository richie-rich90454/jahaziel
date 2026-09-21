/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier of a snapshot.
 *
 * @param value the identifier value
 */
public record SnapshotId(UUID value) {
  public SnapshotId {
    Objects.requireNonNull(value, "value");
  }
}
