/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Opaque handle to prepared workspace storage. Workspaces are plain folders: the persistent home
 * folder plus the ephemeral per-VM jail folder. No disk images, no partitions.
 *
 * @param workspaceId the workspace this storage belongs to
 * @param homePath absolute path to the persistent home folder
 * @param jailPath absolute path to the per-VM jail directory
 */
public record WorkspaceHandle(UUID workspaceId, Path homePath, Path jailPath) {
  public WorkspaceHandle {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(homePath, "homePath");
    Objects.requireNonNull(jailPath, "jailPath");
  }
}
