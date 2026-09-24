/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Human tag of a snapshot, unique per workspace. */
public record SnapshotTag(String value) {
  public SnapshotTag {
    // Every absolute path contains '/' or '\' on its platform, so those two checks already reject
    // absolute tags; no separate isAbsolute check is needed.
    if (value == null
        || value.isBlank()
        || value.contains("..")
        || value.contains("/")
        || value.contains("\\")
        || value.startsWith(".")) {
      throw new IllegalArgumentException("invalid snapshot tag: " + value);
    }
  }
}
