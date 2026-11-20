/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Request to start a workspace runtime with resource limits.
 *
 * @param workspaceId the workspace to start
 * @param vcpuCount virtual CPU count
 * @param memoryMib guest memory in MiB, reserved at start
 * @param diskIopsLimit disk IOPS limit
 * @param diskBandwidthBytesPerSec disk bandwidth limit in bytes per second
 * @param networkBandwidthBytesPerSec network bandwidth limit in bytes per second
 * @param networkConnectionsLimit concurrent connection limit
 * @param pidsLimit process count limit
 * @param kernelCmdlineExtra extra kernel command line arguments
 */
public final class RuntimeRequest {

    private final UUID workspaceId;

    private final int vcpuCount;

    private final long memoryMib;

    private final long diskIopsLimit;

    private final long diskBandwidthBytesPerSec;

    private final long networkBandwidthBytesPerSec;

    private final int networkConnectionsLimit;

    private final int pidsLimit;

    private final List<String> kernelCmdlineExtra;

    public RuntimeRequest(
            UUID workspaceId,
            int vcpuCount,
            long memoryMib,
            long diskIopsLimit,
            long diskBandwidthBytesPerSec,
            long networkBandwidthBytesPerSec,
            int networkConnectionsLimit,
            int pidsLimit,
            List<String> kernelCmdlineExtra) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (vcpuCount < 1) {
            throw new IllegalArgumentException("vcpuCount must be positive");
        }
        if (memoryMib < 1) {
            throw new IllegalArgumentException("memoryMib must be positive");
        }
        Objects.requireNonNull(kernelCmdlineExtra, "kernelCmdlineExtra");
        kernelCmdlineExtra = List.copyOf(kernelCmdlineExtra);
        this.workspaceId = workspaceId;
        this.vcpuCount = vcpuCount;
        this.memoryMib = memoryMib;
        this.diskIopsLimit = diskIopsLimit;
        this.diskBandwidthBytesPerSec = diskBandwidthBytesPerSec;
        this.networkBandwidthBytesPerSec = networkBandwidthBytesPerSec;
        this.networkConnectionsLimit = networkConnectionsLimit;
        this.pidsLimit = pidsLimit;
        this.kernelCmdlineExtra = kernelCmdlineExtra;
    }

    public UUID workspaceId() {
        return this.workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public int vcpuCount() {
        return this.vcpuCount;
    }

    public int getVcpuCount() {
        return vcpuCount;
    }

    public long memoryMib() {
        return this.memoryMib;
    }

    public long getMemoryMib() {
        return memoryMib;
    }

    public long diskIopsLimit() {
        return this.diskIopsLimit;
    }

    public long getDiskIopsLimit() {
        return diskIopsLimit;
    }

    public long diskBandwidthBytesPerSec() {
        return this.diskBandwidthBytesPerSec;
    }

    public long getDiskBandwidthBytesPerSec() {
        return diskBandwidthBytesPerSec;
    }

    public long networkBandwidthBytesPerSec() {
        return this.networkBandwidthBytesPerSec;
    }

    public long getNetworkBandwidthBytesPerSec() {
        return networkBandwidthBytesPerSec;
    }

    public int networkConnectionsLimit() {
        return this.networkConnectionsLimit;
    }

    public int getNetworkConnectionsLimit() {
        return networkConnectionsLimit;
    }

    public int pidsLimit() {
        return this.pidsLimit;
    }

    public int getPidsLimit() {
        return pidsLimit;
    }

    public List<String> kernelCmdlineExtra() {
        return this.kernelCmdlineExtra;
    }

    public List<String> getKernelCmdlineExtra() {
        return kernelCmdlineExtra;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuntimeRequest)) return false;
        RuntimeRequest other = (RuntimeRequest) o;
        return (java.util.Objects.equals(this.workspaceId, other.workspaceId))
                && this.vcpuCount == other.vcpuCount
                && this.memoryMib == other.memoryMib
                && this.diskIopsLimit == other.diskIopsLimit
                && this.diskBandwidthBytesPerSec == other.diskBandwidthBytesPerSec
                && this.networkBandwidthBytesPerSec == other.networkBandwidthBytesPerSec
                && this.networkConnectionsLimit == other.networkConnectionsLimit
                && this.pidsLimit == other.pidsLimit
                && java.util.Objects.equals(this.kernelCmdlineExtra, other.kernelCmdlineExtra);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.workspaceId,
                this.vcpuCount,
                this.memoryMib,
                this.diskIopsLimit,
                this.diskBandwidthBytesPerSec,
                this.networkBandwidthBytesPerSec,
                this.networkConnectionsLimit,
                this.pidsLimit,
                this.kernelCmdlineExtra);
    }

    @Override
    public String toString() {
        return "RuntimeRequest["
                + "workspaceId="
                + this.workspaceId
                + ", "
                + "vcpuCount="
                + this.vcpuCount
                + ", "
                + "memoryMib="
                + this.memoryMib
                + ", "
                + "diskIopsLimit="
                + this.diskIopsLimit
                + ", "
                + "diskBandwidthBytesPerSec="
                + this.diskBandwidthBytesPerSec
                + ", "
                + "networkBandwidthBytesPerSec="
                + this.networkBandwidthBytesPerSec
                + ", "
                + "networkConnectionsLimit="
                + this.networkConnectionsLimit
                + ", "
                + "pidsLimit="
                + this.pidsLimit
                + ", "
                + "kernelCmdlineExtra="
                + this.kernelCmdlineExtra
                + "]";
    }
}
