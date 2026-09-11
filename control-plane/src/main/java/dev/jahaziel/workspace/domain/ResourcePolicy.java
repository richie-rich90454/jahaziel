/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

/**
 * Resource ceilings of a workspace.
 *
 * @param maxMemoryMib memory ceiling in MiB
 * @param maxVcpuCount virtual CPU ceiling
 * @param maxDiskGiB disk ceiling in GiB
 */
public record ResourcePolicy(int maxMemoryMib, int maxVcpuCount, long maxDiskGiB) {
  public ResourcePolicy {
    if (maxMemoryMib < 1) {
      throw new IllegalArgumentException("maxMemoryMib must be positive");
    }
    if (maxVcpuCount < 1) {
      throw new IllegalArgumentException("maxVcpuCount must be positive");
    }
    if (maxDiskGiB < 1) {
      throw new IllegalArgumentException("maxDiskGiB must be positive");
    }
  }

  /** Returns the default ceilings for a new workspace. */
  public static ResourcePolicy defaults() {
    return new ResourcePolicy(2048, 2, 20);
  }
}
