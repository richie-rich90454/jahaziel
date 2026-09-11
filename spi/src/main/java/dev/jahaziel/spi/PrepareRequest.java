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
 * @param image the base image to provision from
 * @param quota the logical quota for the home subvolume
 */
public record PrepareRequest(UUID workspaceId, ImageRef image, QuotaBytes quota) {
  public PrepareRequest {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(image, "image");
    Objects.requireNonNull(quota, "quota");
  }
}
