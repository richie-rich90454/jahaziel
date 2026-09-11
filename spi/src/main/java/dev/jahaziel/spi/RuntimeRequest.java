/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Request to start a workspace runtime with resource limits.
 *
 * @param workspaceId the workspace to start
 * @param vcpuCount virtual CPU count
 * @param memoryMib guest memory in MiB, reserved at start
 * @param diskIopsLimit disk IOPS limit
 * @param diskBandwidthBytesPerSec disk bandwidth limit in bytes per second
 * @param networkBandwidthBytesPerSec network bandwidth limit in bytes per second
 * @param networkConnectionsLimit concurrent connection limit
 * @param pidsLimit process count limit
 * @param kernelCmdlineExtra extra kernel command line arguments
 */
public record RuntimeRequest(
    UUID workspaceId,
    int vcpuCount,
    long memoryMib,
    long diskIopsLimit,
    long diskBandwidthBytesPerSec,
    long networkBandwidthBytesPerSec,
    int networkConnectionsLimit,
    int pidsLimit,
    List<String> kernelCmdlineExtra) {
  public RuntimeRequest {
    Objects.requireNonNull(workspaceId, "workspaceId");
    if (vcpuCount < 1) {
      throw new IllegalArgumentException("vcpuCount must be positive");
    }
    if (memoryMib < 1) {
      throw new IllegalArgumentException("memoryMib must be positive");
    }
    Objects.requireNonNull(kernelCmdlineExtra, "kernelCmdlineExtra");
    kernelCmdlineExtra = List.copyOf(kernelCmdlineExtra);
  }
}
