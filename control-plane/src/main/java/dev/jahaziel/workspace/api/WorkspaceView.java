/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.workspace.domain.Workspace;
import java.time.Instant;
import java.util.Objects;

/**
 * Read model of a workspace. Carries the stored timestamps and usage the domain does not track.
 *
 * @param workspace the workspace
 * @param createdAt when the row was created
 * @param lastActivityAt last state change
 * @param usage storage usage
 */
public record WorkspaceView(
    Workspace workspace, Instant createdAt, Instant lastActivityAt, StorageUsage usage) {
  public WorkspaceView {
    Objects.requireNonNull(workspace, "workspace");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(lastActivityAt, "lastActivityAt");
    Objects.requireNonNull(usage, "usage");
  }
}
