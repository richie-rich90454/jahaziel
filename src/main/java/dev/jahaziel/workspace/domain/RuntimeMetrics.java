/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

/**
 * Latest resource sample of a virtual machine.
 *
 * @param cpuUsagePercent CPU usage percent
 * @param memoryUsedBytes guest memory used in bytes
 * @param memoryTotalBytes guest memory total in bytes
 * @param diskUsedBytes disk used in bytes
 */
public final class RuntimeMetrics {
    private final double cpuUsagePercent;
    private final long memoryUsedBytes;
    private final long memoryTotalBytes;
    private final long diskUsedBytes;

    public RuntimeMetrics(
            double cpuUsagePercent,
            long memoryUsedBytes,
            long memoryTotalBytes,
            long diskUsedBytes) {
        if (cpuUsagePercent < 0) {
            throw new IllegalArgumentException("cpuUsagePercent must not be negative");
        }
        if (memoryUsedBytes < 0) {
            throw new IllegalArgumentException("memoryUsedBytes must not be negative");
        }
        if (memoryTotalBytes < 0) {
            throw new IllegalArgumentException("memoryTotalBytes must not be negative");
        }
        if (diskUsedBytes < 0) {
            throw new IllegalArgumentException("diskUsedBytes must not be negative");
        }

        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryUsedBytes = memoryUsedBytes;
        this.memoryTotalBytes = memoryTotalBytes;
        this.diskUsedBytes = diskUsedBytes;
    }

    public double cpuUsagePercent() {
        return cpuUsagePercent;
    }

    public double getCpuUsagePercent() {
        return cpuUsagePercent;
    }

    public long memoryUsedBytes() {
        return memoryUsedBytes;
    }

    public long getMemoryUsedBytes() {
        return memoryUsedBytes;
    }

    public long memoryTotalBytes() {
        return memoryTotalBytes;
    }

    public long getMemoryTotalBytes() {
        return memoryTotalBytes;
    }

    public long diskUsedBytes() {
        return diskUsedBytes;
    }

    public long getDiskUsedBytes() {
        return diskUsedBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuntimeMetrics other = (RuntimeMetrics) o;
        return cpuUsagePercent == other.cpuUsagePercent
                && memoryUsedBytes == other.memoryUsedBytes
                && memoryTotalBytes == other.memoryTotalBytes
                && diskUsedBytes == other.diskUsedBytes;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result =
                31 * result
                        + (int)
                                (Double.doubleToLongBits(cpuUsagePercent)
                                        ^ (Double.doubleToLongBits(cpuUsagePercent) >>> 32));
        result = 31 * result + (int) (memoryUsedBytes ^ (memoryUsedBytes >>> 32));
        result = 31 * result + (int) (memoryTotalBytes ^ (memoryTotalBytes >>> 32));
        result = 31 * result + (int) (diskUsedBytes ^ (diskUsedBytes >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "RuntimeMetrics{"
                + "cpuUsagePercent="
                + cpuUsagePercent
                + ", memoryUsedBytes="
                + memoryUsedBytes
                + ", memoryTotalBytes="
                + memoryTotalBytes
                + ", diskUsedBytes="
                + diskUsedBytes
                + '}';
    }

    /** Returns zero metrics, used before the first sample. */
    public static RuntimeMetrics zero() {
        return new RuntimeMetrics(0, 0, 0, 0);
    }
}
