/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Human tag of a snapshot, unique per workspace. */
public record SnapshotTag(String value) {
  public SnapshotTag {
    if (value == null
        || value.isBlank()
        || value.contains("..")
        || value.contains("/")
        || value.contains("\\")
        || value.startsWith(".")
        || java.nio.file.Path.of(value).isAbsolute()) {
      throw new IllegalArgumentException("invalid snapshot tag: " + value);
    }
  }
}
