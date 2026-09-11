/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Opaque handle to prepared workspace storage.
 *
 * @param workspaceId the workspace this storage belongs to
 * @param rootDiskPath absolute path to the per-VM root disk
 * @param homeSubvolumePath absolute path to the persistent home subvolume
 * @param jailPath absolute path to the per-VM jail directory
 */
public record WorkspaceHandle(
    UUID workspaceId, Path rootDiskPath, Path homeSubvolumePath, Path jailPath) {
  public WorkspaceHandle {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(rootDiskPath, "rootDiskPath");
    Objects.requireNonNull(homeSubvolumePath, "homeSubvolumePath");
    Objects.requireNonNull(jailPath, "jailPath");
  }
}
