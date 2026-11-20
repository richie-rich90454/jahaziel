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
public final class WorkspaceView {
    private final Workspace workspace;
    private final Instant createdAt;
    private final Instant lastActivityAt;
    private final StorageUsage usage;

    public WorkspaceView(
            Workspace workspace, Instant createdAt, Instant lastActivityAt, StorageUsage usage) {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt");
        Objects.requireNonNull(usage, "usage");

        this.workspace = workspace;
        this.createdAt = createdAt;
        this.lastActivityAt = lastActivityAt;
        this.usage = usage;
    }

    public Workspace workspace() {
        return workspace;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant lastActivityAt() {
        return lastActivityAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public StorageUsage usage() {
        return usage;
    }

    public StorageUsage getUsage() {
        return usage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkspaceView other = (WorkspaceView) o;
        return Objects.equals(workspace, other.workspace)
                && Objects.equals(createdAt, other.createdAt)
                && Objects.equals(lastActivityAt, other.lastActivityAt)
                && Objects.equals(usage, other.usage);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(workspace);
        result = 31 * result + Objects.hashCode(createdAt);
        result = 31 * result + Objects.hashCode(lastActivityAt);
        result = 31 * result + Objects.hashCode(usage);
        return result;
    }

    @Override
    public String toString() {
        return "WorkspaceView{"
                + "workspace="
                + workspace
                + ", createdAt="
                + createdAt
                + ", lastActivityAt="
                + lastActivityAt
                + ", usage="
                + usage
                + '}';
    }
}
