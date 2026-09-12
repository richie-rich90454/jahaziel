/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/**
 * Storage consumption of a workspace.
 *
 * @param logicalBytes sum of file sizes in the home folder
 * @param physicalBytes actual allocated bytes
 * @param snapshotBytes sum of snapshot folder sizes
 * @param deduplicatedBytes savings from sharing, logical minus physical
 */
public record StorageUsage(
    long logicalBytes, long physicalBytes, long snapshotBytes, long deduplicatedBytes) {
  public StorageUsage {
    if (logicalBytes < 0) {
      throw new IllegalArgumentException("logicalBytes must not be negative");
    }
    if (physicalBytes < 0) {
      throw new IllegalArgumentException("physicalBytes must not be negative");
    }
    if (snapshotBytes < 0) {
      throw new IllegalArgumentException("snapshotBytes must not be negative");
    }
  }
}
