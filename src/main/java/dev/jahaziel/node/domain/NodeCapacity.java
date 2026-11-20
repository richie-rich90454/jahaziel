/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import java.util.Objects;

/**
 * Total capacity of a node.
 *
 * @param cpuCores CPU core count
 * @param memoryMib memory in MiB
 * @param diskGib disk in GiB
 */
public final class NodeCapacity {
    private final int cpuCores;
    private final long memoryMib;
    private final long diskGib;

    /**
     * Creates a capacity.
     *
     * @param cpuCores CPU core count
     * @param memoryMib memory in MiB
     * @param diskGib disk in GiB
     */
    public NodeCapacity(int cpuCores, long memoryMib, long diskGib) {
        if (cpuCores < 1) {
            throw new IllegalArgumentException("cpuCores must be positive");
        }
        if (memoryMib < 1) {
            throw new IllegalArgumentException("memoryMib must be positive");
        }
        if (diskGib < 1) {
            throw new IllegalArgumentException("diskGib must be positive");
        }
        this.cpuCores = cpuCores;
        this.memoryMib = memoryMib;
        this.diskGib = diskGib;
    }

    /** Returns the CPU core count. */
    public int cpuCores() {
        return cpuCores;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    /** Returns memory in MiB. */
    public long memoryMib() {
        return memoryMib;
    }

    public long getMemoryMib() {
        return memoryMib;
    }

    /** Returns disk in GiB. */
    public long diskGib() {
        return diskGib;
    }

    public long getDiskGib() {
        return diskGib;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        NodeCapacity that = (NodeCapacity) other;
        return cpuCores == that.cpuCores && memoryMib == that.memoryMib && diskGib == that.diskGib;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpuCores, memoryMib, diskGib);
    }

    @Override
    public String toString() {
        return "NodeCapacity[cpuCores="
                + cpuCores
                + ", memoryMib="
                + memoryMib
                + ", diskGib="
                + diskGib
                + "]";
    }
}
