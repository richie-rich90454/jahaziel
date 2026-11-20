/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

/**
 * Resource ceilings of a workspace.
 *
 * @param maxMemoryMib memory ceiling in MiB
 * @param maxVcpuCount virtual CPU ceiling
 * @param maxDiskGiB disk ceiling in GiB
 */
public final class ResourcePolicy {
    private final int maxMemoryMib;
    private final int maxVcpuCount;
    private final long maxDiskGiB;

    public ResourcePolicy(int maxMemoryMib, int maxVcpuCount, long maxDiskGiB) {
        if (maxMemoryMib < 1) {
            throw new IllegalArgumentException("maxMemoryMib must be positive");
        }
        if (maxVcpuCount < 1) {
            throw new IllegalArgumentException("maxVcpuCount must be positive");
        }
        if (maxDiskGiB < 1) {
            throw new IllegalArgumentException("maxDiskGiB must be positive");
        }

        this.maxMemoryMib = maxMemoryMib;
        this.maxVcpuCount = maxVcpuCount;
        this.maxDiskGiB = maxDiskGiB;
    }

    public int maxMemoryMib() {
        return maxMemoryMib;
    }

    public int getMaxMemoryMib() {
        return maxMemoryMib;
    }

    public int maxVcpuCount() {
        return maxVcpuCount;
    }

    public int getMaxVcpuCount() {
        return maxVcpuCount;
    }

    public long maxDiskGiB() {
        return maxDiskGiB;
    }

    public long getMaxDiskGiB() {
        return maxDiskGiB;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourcePolicy other = (ResourcePolicy) o;
        return maxMemoryMib == other.maxMemoryMib
                && maxVcpuCount == other.maxVcpuCount
                && maxDiskGiB == other.maxDiskGiB;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + (int) maxMemoryMib;
        result = 31 * result + (int) maxVcpuCount;
        result = 31 * result + (int) (maxDiskGiB ^ (maxDiskGiB >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "ResourcePolicy{"
                + "maxMemoryMib="
                + maxMemoryMib
                + ", maxVcpuCount="
                + maxVcpuCount
                + ", maxDiskGiB="
                + maxDiskGiB
                + '}';
    }

    /** Returns the default ceilings for a new workspace. */
    public static ResourcePolicy defaults() {
        return new ResourcePolicy(2048, 2, 20);
    }
}
