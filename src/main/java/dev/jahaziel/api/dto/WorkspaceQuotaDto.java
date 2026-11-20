/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

/**
 * Quota view of a workspace. Used bytes stay zero until the metrics slice supplies live values.
 *
 * @param logicalBytesUsed logical bytes used
 * @param logicalBytesLimit logical byte limit
 * @param inodeCountUsed inodes used
 * @param inodeCountLimit inode limit
 */
public final class WorkspaceQuotaDto {

    private final long logicalBytesUsed;

    private final long logicalBytesLimit;

    private final long inodeCountUsed;

    private final long inodeCountLimit;

    public WorkspaceQuotaDto(
            long logicalBytesUsed,
            long logicalBytesLimit,
            long inodeCountUsed,
            long inodeCountLimit) {
        this.logicalBytesUsed = logicalBytesUsed;
        this.logicalBytesLimit = logicalBytesLimit;
        this.inodeCountUsed = inodeCountUsed;
        this.inodeCountLimit = inodeCountLimit;
    }

    public long logicalBytesUsed() {
        return this.logicalBytesUsed;
    }

    public long getLogicalBytesUsed() {
        return this.logicalBytesUsed;
    }

    public long logicalBytesLimit() {
        return this.logicalBytesLimit;
    }

    public long getLogicalBytesLimit() {
        return this.logicalBytesLimit;
    }

    public long inodeCountUsed() {
        return this.inodeCountUsed;
    }

    public long getInodeCountUsed() {
        return this.inodeCountUsed;
    }

    public long inodeCountLimit() {
        return this.inodeCountLimit;
    }

    public long getInodeCountLimit() {
        return this.inodeCountLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkspaceQuotaDto)) return false;
        WorkspaceQuotaDto other = (WorkspaceQuotaDto) o;
        return (this.logicalBytesUsed == other.logicalBytesUsed)
                && this.logicalBytesLimit == other.logicalBytesLimit
                && this.inodeCountUsed == other.inodeCountUsed
                && this.inodeCountLimit == other.inodeCountLimit;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.logicalBytesUsed,
                this.logicalBytesLimit,
                this.inodeCountUsed,
                this.inodeCountLimit);
    }

    @Override
    public String toString() {
        return "WorkspaceQuotaDto["
                + "logicalBytesUsed="
                + this.logicalBytesUsed
                + ", "
                + "logicalBytesLimit="
                + this.logicalBytesLimit
                + ", "
                + "inodeCountUsed="
                + this.inodeCountUsed
                + ", "
                + "inodeCountLimit="
                + this.inodeCountLimit
                + "]";
    }
}
