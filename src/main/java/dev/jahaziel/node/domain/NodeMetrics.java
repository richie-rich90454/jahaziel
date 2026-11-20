/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import java.util.Objects;

/**
 * Latest resource sample of a node.
 *
 * @param cpuUsagePercent CPU usage percent
 * @param memoryUsedBytes memory used in bytes
 * @param memoryTotalBytes memory total in bytes
 * @param loadAverage1m one minute load average
 */
public final class NodeMetrics {
    private final double cpuUsagePercent;
    private final long memoryUsedBytes;
    private final long memoryTotalBytes;
    private final double loadAverage1m;

    /**
     * Creates a sample.
     *
     * @param cpuUsagePercent CPU usage percent
     * @param memoryUsedBytes memory used in bytes
     * @param memoryTotalBytes memory total in bytes
     * @param loadAverage1m one minute load average
     */
    public NodeMetrics(
            double cpuUsagePercent,
            long memoryUsedBytes,
            long memoryTotalBytes,
            double loadAverage1m) {
        if (cpuUsagePercent < 0) {
            throw new IllegalArgumentException("cpuUsagePercent must not be negative");
        }
        if (memoryUsedBytes < 0) {
            throw new IllegalArgumentException("memoryUsedBytes must not be negative");
        }
        if (memoryTotalBytes < 0) {
            throw new IllegalArgumentException("memoryTotalBytes must not be negative");
        }
        if (loadAverage1m < 0) {
            throw new IllegalArgumentException("loadAverage1m must not be negative");
        }
        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryUsedBytes = memoryUsedBytes;
        this.memoryTotalBytes = memoryTotalBytes;
        this.loadAverage1m = loadAverage1m;
    }

    /** Returns the CPU usage percent. */
    public double cpuUsagePercent() {
        return cpuUsagePercent;
    }

    public double getCpuUsagePercent() {
        return cpuUsagePercent;
    }

    /** Returns memory used in bytes. */
    public long memoryUsedBytes() {
        return memoryUsedBytes;
    }

    public long getMemoryUsedBytes() {
        return memoryUsedBytes;
    }

    /** Returns memory total in bytes. */
    public long memoryTotalBytes() {
        return memoryTotalBytes;
    }

    public long getMemoryTotalBytes() {
        return memoryTotalBytes;
    }

    /** Returns the one minute load average. */
    public double loadAverage1m() {
        return loadAverage1m;
    }

    public double getLoadAverage1m() {
        return loadAverage1m;
    }

    /** Returns zero metrics, used before the first sample. */
    public static NodeMetrics zero() {
        return new NodeMetrics(0, 0, 0, 0);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        NodeMetrics that = (NodeMetrics) other;
        return Double.compare(cpuUsagePercent, that.cpuUsagePercent) == 0
                && memoryUsedBytes == that.memoryUsedBytes
                && memoryTotalBytes == that.memoryTotalBytes
                && Double.compare(loadAverage1m, that.loadAverage1m) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpuUsagePercent, memoryUsedBytes, memoryTotalBytes, loadAverage1m);
    }

    @Override
    public String toString() {
        return "NodeMetrics[cpuUsagePercent="
                + cpuUsagePercent
                + ", memoryUsedBytes="
                + memoryUsedBytes
                + ", memoryTotalBytes="
                + memoryTotalBytes
                + ", loadAverage1m="
                + loadAverage1m
                + "]";
    }
}
