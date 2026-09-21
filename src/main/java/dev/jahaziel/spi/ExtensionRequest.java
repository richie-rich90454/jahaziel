/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.Objects;
import java.util.UUID;

/**
 * Request to install an extension.
 *
 * @param extensionId the extension identifier, publisher dot name
 * @param version the requested version
 * @param userId the installing user
 * @param workspaceId the target workspace
 */
public record ExtensionRequest(String extensionId, String version, UUID userId, UUID workspaceId) {
  public ExtensionRequest {
    if (extensionId == null || extensionId.isBlank()) {
      throw new IllegalArgumentException("extensionId must not be blank");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("version must not be blank");
    }
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(workspaceId, "workspaceId");
  }
}
