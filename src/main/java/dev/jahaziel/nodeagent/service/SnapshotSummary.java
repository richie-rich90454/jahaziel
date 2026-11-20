/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import dev.jahaziel.spi.SnapshotHandle;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A listed snapshot.
 *
 * @param handle the snapshot handle
 * @param tag the human tag
 * @param createdAt when the snapshot was taken
 */
public final class SnapshotSummary {
    private final SnapshotHandle handle;
    private final String tag;
    private final Instant createdAt;

    /**
     * Creates a summary.
     *
     * @param handle the snapshot handle
     * @param tag the human tag
     * @param createdAt when the snapshot was taken
     */
    public SnapshotSummary(SnapshotHandle handle, String tag, Instant createdAt) {
        Objects.requireNonNull(handle, "handle");
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        this.handle = handle;
        this.tag = tag;
        this.createdAt = createdAt;
    }

    /** Returns the snapshot handle. */
    public SnapshotHandle handle() {
        return handle;
    }

    public SnapshotHandle getHandle() {
        return handle;
    }

    /** Returns the human tag. */
    public String tag() {
        return tag;
    }

    public String getTag() {
        return tag;
    }

    /** Returns when the snapshot was taken. */
    public Instant createdAt() {
        return createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Returns the snapshot folder path. */
    public Path snapshotPath() {
        return handle.snapshotPath();
    }

    /** Returns the snapshot identifier. */
    public UUID snapshotId() {
        return handle.snapshotId();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        SnapshotSummary that = (SnapshotSummary) other;
        return handle.equals(that.handle)
                && tag.equals(that.tag)
                && createdAt.equals(that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(handle, tag, createdAt);
    }

    @Override
    public String toString() {
        return "SnapshotSummary[handle="
                + handle
                + ", tag="
                + tag
                + ", createdAt="
                + createdAt
                + "]";
    }
}
