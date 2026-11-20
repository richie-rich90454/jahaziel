/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.monitoring.api.HourlyRollup;

/**
 * Current metrics of a workspace for reads. Zeros until the sampler feeds live values. Memory
 * figures are hourly aggregates, not live gauges or capacity.
 *
 * @param cpuUsagePercent CPU usage percent
 * @param memoryUsedBytes guest memory used in bytes
 * @param memoryPeakBytes guest memory peak in bytes
 * @param diskReadBytesPerSec disk read bytes per second
 * @param networkRxBytesPerSec network receive bytes per second
 * @param networkTxBytesPerSec network transmit bytes per second
 * @param processCount process count
 */
public final class MetricsDto {

    private final double cpuUsagePercent;

    private final long memoryUsedBytes;

    private final long memoryPeakBytes;

    private final long diskReadBytesPerSec;

    private final long networkRxBytesPerSec;

    private final long networkTxBytesPerSec;

    private final int processCount;

    public MetricsDto(
            double cpuUsagePercent,
            long memoryUsedBytes,
            long memoryPeakBytes,
            long diskReadBytesPerSec,
            long networkRxBytesPerSec,
            long networkTxBytesPerSec,
            int processCount) {
        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryUsedBytes = memoryUsedBytes;
        this.memoryPeakBytes = memoryPeakBytes;
        this.diskReadBytesPerSec = diskReadBytesPerSec;
        this.networkRxBytesPerSec = networkRxBytesPerSec;
        this.networkTxBytesPerSec = networkTxBytesPerSec;
        this.processCount = processCount;
    }

    /**
     * Maps a rollup to a DTO.
     *
     * @param rollup the rollup
     * @return the DTO
     */
    public static MetricsDto from(HourlyRollup rollup) {
        return new MetricsDto(
                rollup.cpuAvgPercent(),
                rollup.memoryAvgMib() * 1024 * 1024,
                rollup.memoryMaxMib() * 1024 * 1024,
                rollup.diskReadAvgBps(),
                rollup.networkRxAvgBps(),
                rollup.networkTxAvgBps(),
                rollup.processMax());
    }

    /**
     * Returns zero metrics. Served when no rollup exists yet.
     *
     * @return the zero DTO
     */
    public static MetricsDto zero() {
        return new MetricsDto(0, 0, 0, 0, 0, 0, 0);
    }

    public double cpuUsagePercent() {
        return this.cpuUsagePercent;
    }

    public double getCpuUsagePercent() {
        return this.cpuUsagePercent;
    }

    public long memoryUsedBytes() {
        return this.memoryUsedBytes;
    }

    public long getMemoryUsedBytes() {
        return this.memoryUsedBytes;
    }

    public long memoryPeakBytes() {
        return this.memoryPeakBytes;
    }

    public long getMemoryPeakBytes() {
        return this.memoryPeakBytes;
    }

    public long diskReadBytesPerSec() {
        return this.diskReadBytesPerSec;
    }

    public long getDiskReadBytesPerSec() {
        return this.diskReadBytesPerSec;
    }

    public long networkRxBytesPerSec() {
        return this.networkRxBytesPerSec;
    }

    public long getNetworkRxBytesPerSec() {
        return this.networkRxBytesPerSec;
    }

    public long networkTxBytesPerSec() {
        return this.networkTxBytesPerSec;
    }

    public long getNetworkTxBytesPerSec() {
        return this.networkTxBytesPerSec;
    }

    public int processCount() {
        return this.processCount;
    }

    public int getProcessCount() {
        return this.processCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetricsDto)) return false;
        MetricsDto other = (MetricsDto) o;
        return (this.cpuUsagePercent == other.cpuUsagePercent)
                && this.memoryUsedBytes == other.memoryUsedBytes
                && this.memoryPeakBytes == other.memoryPeakBytes
                && this.diskReadBytesPerSec == other.diskReadBytesPerSec
                && this.networkRxBytesPerSec == other.networkRxBytesPerSec
                && this.networkTxBytesPerSec == other.networkTxBytesPerSec
                && this.processCount == other.processCount;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.cpuUsagePercent,
                this.memoryUsedBytes,
                this.memoryPeakBytes,
                this.diskReadBytesPerSec,
                this.networkRxBytesPerSec,
                this.networkTxBytesPerSec,
                this.processCount);
    }

    @Override
    public String toString() {
        return "MetricsDto["
                + "cpuUsagePercent="
                + this.cpuUsagePercent
                + ", "
                + "memoryUsedBytes="
                + this.memoryUsedBytes
                + ", "
                + "memoryPeakBytes="
                + this.memoryPeakBytes
                + ", "
                + "diskReadBytesPerSec="
                + this.diskReadBytesPerSec
                + ", "
                + "networkRxBytesPerSec="
                + this.networkRxBytesPerSec
                + ", "
                + "networkTxBytesPerSec="
                + this.networkTxBytesPerSec
                + ", "
                + "processCount="
                + this.processCount
                + "]";
    }
}
