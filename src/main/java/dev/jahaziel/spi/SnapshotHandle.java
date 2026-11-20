/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Opaque handle to a snapshot of a workspace home folder.
 *
 * @param snapshotId the snapshot identifier
 * @param snapshotPath absolute path to the read-only snapshot folder
 * @param createdAt when the snapshot was taken
 */
public final class SnapshotHandle {

    private final UUID snapshotId;

    private final Path snapshotPath;

    private final Instant createdAt;

    public SnapshotHandle(UUID snapshotId, Path snapshotPath, Instant createdAt) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(snapshotPath, "snapshotPath");
        Objects.requireNonNull(createdAt, "createdAt");
        this.snapshotId = snapshotId;
        this.snapshotPath = snapshotPath;
        this.createdAt = createdAt;
    }

    public UUID snapshotId() {
        return this.snapshotId;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public Path snapshotPath() {
        return this.snapshotPath;
    }

    public Path getSnapshotPath() {
        return snapshotPath;
    }

    public Instant createdAt() {
        return this.createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SnapshotHandle)) return false;
        SnapshotHandle other = (SnapshotHandle) o;
        return (java.util.Objects.equals(this.snapshotId, other.snapshotId))
                && java.util.Objects.equals(this.snapshotPath, other.snapshotPath)
                && java.util.Objects.equals(this.createdAt, other.createdAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.snapshotId, this.snapshotPath, this.createdAt);
    }

    @Override
    public String toString() {
        return "SnapshotHandle["
                + "snapshotId="
                + this.snapshotId
                + ", "
                + "snapshotPath="
                + this.snapshotPath
                + ", "
                + "createdAt="
                + this.createdAt
                + "]";
    }
}
