/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import java.util.Objects;

/**
 * One host resource sample for the control plane collector.
 *
 * @param cpuUsagePercent CPU usage percent
 * @param memoryUsedBytes memory used in bytes
 * @param memoryTotalBytes memory total in bytes
 * @param diskUsedBytes disk used in bytes
 * @param diskTotalBytes disk total in bytes
 * @param loadAverage1m one minute load average
 * @param loadAverage5m five minute load average
 * @param loadAverage15m fifteen minute load average
 */
public final class HostSample {
    private final double cpuUsagePercent;
    private final long memoryUsedBytes;
    private final long memoryTotalBytes;
    private final long diskUsedBytes;
    private final long diskTotalBytes;
    private final double loadAverage1m;
    private final double loadAverage5m;
    private final double loadAverage15m;

    /**
     * Creates a sample.
     *
     * @param cpuUsagePercent CPU usage percent
     * @param memoryUsedBytes memory used in bytes
     * @param memoryTotalBytes memory total in bytes
     * @param diskUsedBytes disk used in bytes
     * @param diskTotalBytes disk total in bytes
     * @param loadAverage1m one minute load average
     * @param loadAverage5m five minute load average
     * @param loadAverage15m fifteen minute load average
     */
    public HostSample(
            double cpuUsagePercent,
            long memoryUsedBytes,
            long memoryTotalBytes,
            long diskUsedBytes,
            long diskTotalBytes,
            double loadAverage1m,
            double loadAverage5m,
            double loadAverage15m) {
        if (cpuUsagePercent < 0) {
            throw new IllegalArgumentException("cpuUsagePercent must not be negative");
        }
        if (memoryUsedBytes < 0 || memoryTotalBytes < 0) {
            throw new IllegalArgumentException("memory values must not be negative");
        }
        if (diskUsedBytes < 0 || diskTotalBytes < 0) {
            throw new IllegalArgumentException("disk values must not be negative");
        }
        Objects.requireNonNull(cpuUsagePercent, "cpuUsagePercent");
        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryUsedBytes = memoryUsedBytes;
        this.memoryTotalBytes = memoryTotalBytes;
        this.diskUsedBytes = diskUsedBytes;
        this.diskTotalBytes = diskTotalBytes;
        this.loadAverage1m = loadAverage1m;
        this.loadAverage5m = loadAverage5m;
        this.loadAverage15m = loadAverage15m;
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

    /** Returns disk used in bytes. */
    public long diskUsedBytes() {
        return diskUsedBytes;
    }

    public long getDiskUsedBytes() {
        return diskUsedBytes;
    }

    /** Returns disk total in bytes. */
    public long diskTotalBytes() {
        return diskTotalBytes;
    }

    public long getDiskTotalBytes() {
        return diskTotalBytes;
    }

    /** Returns the one minute load average. */
    public double loadAverage1m() {
        return loadAverage1m;
    }

    public double getLoadAverage1m() {
        return loadAverage1m;
    }

    /** Returns the five minute load average. */
    public double loadAverage5m() {
        return loadAverage5m;
    }

    public double getLoadAverage5m() {
        return loadAverage5m;
    }

    /** Returns the fifteen minute load average. */
    public double loadAverage15m() {
        return loadAverage15m;
    }

    public double getLoadAverage15m() {
        return loadAverage15m;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        HostSample that = (HostSample) other;
        return Double.compare(cpuUsagePercent, that.cpuUsagePercent) == 0
                && memoryUsedBytes == that.memoryUsedBytes
                && memoryTotalBytes == that.memoryTotalBytes
                && diskUsedBytes == that.diskUsedBytes
                && diskTotalBytes == that.diskTotalBytes
                && Double.compare(loadAverage1m, that.loadAverage1m) == 0
                && Double.compare(loadAverage5m, that.loadAverage5m) == 0
                && Double.compare(loadAverage15m, that.loadAverage15m) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                cpuUsagePercent,
                memoryUsedBytes,
                memoryTotalBytes,
                diskUsedBytes,
                diskTotalBytes,
                loadAverage1m,
                loadAverage5m,
                loadAverage15m);
    }

    @Override
    public String toString() {
        return "HostSample[cpuUsagePercent="
                + cpuUsagePercent
                + ", memoryUsedBytes="
                + memoryUsedBytes
                + ", memoryTotalBytes="
                + memoryTotalBytes
                + ", diskUsedBytes="
                + diskUsedBytes
                + ", diskTotalBytes="
                + diskTotalBytes
                + ", loadAverage1m="
                + loadAverage1m
                + ", loadAverage5m="
                + loadAverage5m
                + ", loadAverage15m="
                + loadAverage15m
                + "]";
    }
}
