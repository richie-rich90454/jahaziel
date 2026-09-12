/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.Objects;
import java.util.UUID;

/**
 * Request to prepare workspace storage and runtime shell.
 *
 * @param workspaceId the workspace to prepare
 * @param ownerId the owning user, keying the persistent home
 * @param image the base image to provision from
 * @param quota the logical quota for the home subvolume
 * @param vcpuCount virtual CPU count
 * @param memoryMib guest memory in MiB, reserved at start
 */
public record PrepareRequest(
    UUID workspaceId,
    UUID ownerId,
    ImageRef image,
    QuotaBytes quota,
    int vcpuCount,
    long memoryMib) {
  public PrepareRequest {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(image, "image");
    Objects.requireNonNull(quota, "quota");
    if (vcpuCount < 1) {
      throw new IllegalArgumentException("vcpuCount must be positive");
    }
    if (memoryMib < 1) {
      throw new IllegalArgumentException("memoryMib must be positive");
    }
  }
}
