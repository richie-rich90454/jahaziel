/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

/**
 * Logical quota of a workspace. Only actual consumption matters physically.
 *
 * @param logicalBytes logical byte limit of the home folder
 * @param inodeLimit inode limit of the home folder
 */
public final class WorkspaceQuota {
    private final long logicalBytes;
    private final long inodeLimit;

    public WorkspaceQuota(long logicalBytes, long inodeLimit) {
        if (logicalBytes < 0) {
            throw new IllegalArgumentException("logicalBytes must not be negative");
        }
        if (inodeLimit < 0) {
            throw new IllegalArgumentException("inodeLimit must not be negative");
        }

        this.logicalBytes = logicalBytes;
        this.inodeLimit = inodeLimit;
    }

    public long logicalBytes() {
        return logicalBytes;
    }

    public long getLogicalBytes() {
        return logicalBytes;
    }

    public long inodeLimit() {
        return inodeLimit;
    }

    public long getInodeLimit() {
        return inodeLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkspaceQuota other = (WorkspaceQuota) o;
        return logicalBytes == other.logicalBytes && inodeLimit == other.inodeLimit;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + (int) (logicalBytes ^ (logicalBytes >>> 32));
        result = 31 * result + (int) (inodeLimit ^ (inodeLimit >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "WorkspaceQuota{"
                + "logicalBytes="
                + logicalBytes
                + ", inodeLimit="
                + inodeLimit
                + '}';
    }

    /** Returns the default quota for a new workspace. */
    public static WorkspaceQuota defaults() {
        return new WorkspaceQuota(21474836480L, 1000000L);
    }
}
