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
public final class WorkspaceHandle {

    private final UUID workspaceId;

    private final Path homePath;

    private final Path jailPath;

    public WorkspaceHandle(UUID workspaceId, Path homePath, Path jailPath) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(homePath, "homePath");
        Objects.requireNonNull(jailPath, "jailPath");
        this.workspaceId = workspaceId;
        this.homePath = homePath;
        this.jailPath = jailPath;
    }

    public UUID workspaceId() {
        return this.workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public Path homePath() {
        return this.homePath;
    }

    public Path getHomePath() {
        return homePath;
    }

    public Path jailPath() {
        return this.jailPath;
    }

    public Path getJailPath() {
        return jailPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkspaceHandle)) return false;
        WorkspaceHandle other = (WorkspaceHandle) o;
        return (java.util.Objects.equals(this.workspaceId, other.workspaceId))
                && java.util.Objects.equals(this.homePath, other.homePath)
                && java.util.Objects.equals(this.jailPath, other.jailPath);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.workspaceId, this.homePath, this.jailPath);
    }

    @Override
    public String toString() {
        return "WorkspaceHandle["
                + "workspaceId="
                + this.workspaceId
                + ", "
                + "homePath="
                + this.homePath
                + ", "
                + "jailPath="
                + this.jailPath
                + "]";
    }
}
