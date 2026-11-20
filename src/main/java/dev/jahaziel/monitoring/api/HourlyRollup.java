/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One hourly resource rollup of a workspace. Components: workspaceId the workspace, hourStart the
 * hour start, cpuAvgPercent average CPU percent, cpuMaxPercent maximum CPU percent, memoryAvgMib
 * average memory in MiB, memoryMaxMib maximum memory in MiB, diskReadAvgBps average disk read bytes
 * per second, diskWriteAvgBps average disk write bytes per second, networkRxAvgBps average network
 * receive bytes per second, networkTxAvgBps average network transmit bytes per second, processMax
 * maximum process count.
 */
public final class HourlyRollup {
    private final UUID workspaceId;
    private final Instant hourStart;
    private final float cpuAvgPercent;
    private final float cpuMaxPercent;
    private final long memoryAvgMib;
    private final long memoryMaxMib;
    private final long diskReadAvgBps;
    private final long diskWriteAvgBps;
    private final long networkRxAvgBps;
    private final long networkTxAvgBps;
    private final int processMax;

    public HourlyRollup(
            UUID workspaceId,
            Instant hourStart,
            float cpuAvgPercent,
            float cpuMaxPercent,
            long memoryAvgMib,
            long memoryMaxMib,
            long diskReadAvgBps,
            long diskWriteAvgBps,
            long networkRxAvgBps,
            long networkTxAvgBps,
            int processMax) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(hourStart, "hourStart");
        if (cpuAvgPercent < 0 || cpuMaxPercent < 0) {
            throw new IllegalArgumentException("cpu values must not be negative");
        }
        if (memoryAvgMib < 0 || memoryMaxMib < 0) {
            throw new IllegalArgumentException("memory values must not be negative");
        }
        if (diskReadAvgBps < 0 || diskWriteAvgBps < 0) {
            throw new IllegalArgumentException("disk values must not be negative");
        }
        if (networkRxAvgBps < 0 || networkTxAvgBps < 0) {
            throw new IllegalArgumentException("network values must not be negative");
        }
        if (processMax < 0) {
            throw new IllegalArgumentException("processMax must not be negative");
        }
        this.workspaceId = workspaceId;
        this.hourStart = hourStart;
        this.cpuAvgPercent = cpuAvgPercent;
        this.cpuMaxPercent = cpuMaxPercent;
        this.memoryAvgMib = memoryAvgMib;
        this.memoryMaxMib = memoryMaxMib;
        this.diskReadAvgBps = diskReadAvgBps;
        this.diskWriteAvgBps = diskWriteAvgBps;
        this.networkRxAvgBps = networkRxAvgBps;
        this.networkTxAvgBps = networkTxAvgBps;
        this.processMax = processMax;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public Instant hourStart() {
        return hourStart;
    }

    public Instant getHourStart() {
        return hourStart;
    }

    public float cpuAvgPercent() {
        return cpuAvgPercent;
    }

    public float getCpuAvgPercent() {
        return cpuAvgPercent;
    }

    public float cpuMaxPercent() {
        return cpuMaxPercent;
    }

    public float getCpuMaxPercent() {
        return cpuMaxPercent;
    }

    public long memoryAvgMib() {
        return memoryAvgMib;
    }

    public long getMemoryAvgMib() {
        return memoryAvgMib;
    }

    public long memoryMaxMib() {
        return memoryMaxMib;
    }

    public long getMemoryMaxMib() {
        return memoryMaxMib;
    }

    public long diskReadAvgBps() {
        return diskReadAvgBps;
    }

    public long getDiskReadAvgBps() {
        return diskReadAvgBps;
    }

    public long diskWriteAvgBps() {
        return diskWriteAvgBps;
    }

    public long getDiskWriteAvgBps() {
        return diskWriteAvgBps;
    }

    public long networkRxAvgBps() {
        return networkRxAvgBps;
    }

    public long getNetworkRxAvgBps() {
        return networkRxAvgBps;
    }

    public long networkTxAvgBps() {
        return networkTxAvgBps;
    }

    public long getNetworkTxAvgBps() {
        return networkTxAvgBps;
    }

    public int processMax() {
        return processMax;
    }

    public int getProcessMax() {
        return processMax;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HourlyRollup)) return false;
        HourlyRollup other = (HourlyRollup) o;
        return Float.compare(cpuAvgPercent, other.cpuAvgPercent) == 0
                && Float.compare(cpuMaxPercent, other.cpuMaxPercent) == 0
                && memoryAvgMib == other.memoryAvgMib
                && memoryMaxMib == other.memoryMaxMib
                && diskReadAvgBps == other.diskReadAvgBps
                && diskWriteAvgBps == other.diskWriteAvgBps
                && networkRxAvgBps == other.networkRxAvgBps
                && networkTxAvgBps == other.networkTxAvgBps
                && processMax == other.processMax
                && workspaceId.equals(other.workspaceId)
                && hourStart.equals(other.hourStart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                workspaceId,
                hourStart,
                cpuAvgPercent,
                cpuMaxPercent,
                memoryAvgMib,
                memoryMaxMib,
                diskReadAvgBps,
                diskWriteAvgBps,
                networkRxAvgBps,
                networkTxAvgBps,
                processMax);
    }

    @Override
    public String toString() {
        return "HourlyRollup[workspaceId="
                + workspaceId
                + ", hourStart="
                + hourStart
                + ", cpuAvgPercent="
                + cpuAvgPercent
                + ", cpuMaxPercent="
                + cpuMaxPercent
                + ", memoryAvgMib="
                + memoryAvgMib
                + ", memoryMaxMib="
                + memoryMaxMib
                + ", diskReadAvgBps="
                + diskReadAvgBps
                + ", diskWriteAvgBps="
                + diskWriteAvgBps
                + ", networkRxAvgBps="
                + networkRxAvgBps
                + ", networkTxAvgBps="
                + networkTxAvgBps
                + ", processMax="
                + processMax
                + "]";
    }
}
