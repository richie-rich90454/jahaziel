/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/**
 * Storage consumption of a workspace.
 *
 * @param logicalBytes sum of file sizes in the home folder
 * @param physicalBytes actual allocated bytes
 * @param snapshotBytes sum of snapshot folder sizes
 * @param deduplicatedBytes savings from sharing, logical minus physical
 */
public final class StorageUsage {

    private final long logicalBytes;

    private final long physicalBytes;

    private final long snapshotBytes;

    private final long deduplicatedBytes;

    public StorageUsage(
            long logicalBytes, long physicalBytes, long snapshotBytes, long deduplicatedBytes) {
        if (logicalBytes < 0) {
            throw new IllegalArgumentException("logicalBytes must not be negative");
        }
        if (physicalBytes < 0) {
            throw new IllegalArgumentException("physicalBytes must not be negative");
        }
        if (snapshotBytes < 0) {
            throw new IllegalArgumentException("snapshotBytes must not be negative");
        }
        if (deduplicatedBytes < 0) {
            throw new IllegalArgumentException("deduplicatedBytes must not be negative");
        }
        this.logicalBytes = logicalBytes;
        this.physicalBytes = physicalBytes;
        this.snapshotBytes = snapshotBytes;
        this.deduplicatedBytes = deduplicatedBytes;
    }

    public long logicalBytes() {
        return this.logicalBytes;
    }

    public long getLogicalBytes() {
        return logicalBytes;
    }

    public long physicalBytes() {
        return this.physicalBytes;
    }

    public long getPhysicalBytes() {
        return physicalBytes;
    }

    public long snapshotBytes() {
        return this.snapshotBytes;
    }

    public long getSnapshotBytes() {
        return snapshotBytes;
    }

    public long deduplicatedBytes() {
        return this.deduplicatedBytes;
    }

    public long getDeduplicatedBytes() {
        return deduplicatedBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StorageUsage)) return false;
        StorageUsage other = (StorageUsage) o;
        return (this.logicalBytes == other.logicalBytes)
                && this.physicalBytes == other.physicalBytes
                && this.snapshotBytes == other.snapshotBytes
                && this.deduplicatedBytes == other.deduplicatedBytes;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.logicalBytes, this.physicalBytes, this.snapshotBytes, this.deduplicatedBytes);
    }

    @Override
    public String toString() {
        return "StorageUsage["
                + "logicalBytes="
                + this.logicalBytes
                + ", "
                + "physicalBytes="
                + this.physicalBytes
                + ", "
                + "snapshotBytes="
                + this.snapshotBytes
                + ", "
                + "deduplicatedBytes="
                + this.deduplicatedBytes
                + "]";
    }
}
