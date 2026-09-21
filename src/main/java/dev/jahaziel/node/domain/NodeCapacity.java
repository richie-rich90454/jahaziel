/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

/**
 * Total capacity of a node.
 *
 * @param cpuCores CPU core count
 * @param memoryMib memory in MiB
 * @param diskGib disk in GiB
 */
public record NodeCapacity(int cpuCores, long memoryMib, long diskGib) {
  public NodeCapacity {
    if (cpuCores < 1) {
      throw new IllegalArgumentException("cpuCores must be positive");
    }
    if (memoryMib < 1) {
      throw new IllegalArgumentException("memoryMib must be positive");
    }
    if (diskGib < 1) {
      throw new IllegalArgumentException("diskGib must be positive");
    }
  }
}
