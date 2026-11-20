/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

/**
 * Sizing of a virtual machine. Memory is reserved at start.
 *
 * @param vcpuCount virtual CPU count
 * @param memoryMib guest memory in MiB
 */
public final class VirtualMachineConfiguration {
    private final int vcpuCount;
    private final long memoryMib;

    public VirtualMachineConfiguration(int vcpuCount, long memoryMib) {
        if (vcpuCount < 1) {
            throw new IllegalArgumentException("vcpuCount must be positive");
        }
        if (memoryMib < 1) {
            throw new IllegalArgumentException("memoryMib must be positive");
        }

        this.vcpuCount = vcpuCount;
        this.memoryMib = memoryMib;
    }

    public int vcpuCount() {
        return vcpuCount;
    }

    public int getVcpuCount() {
        return vcpuCount;
    }

    public long memoryMib() {
        return memoryMib;
    }

    public long getMemoryMib() {
        return memoryMib;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VirtualMachineConfiguration other = (VirtualMachineConfiguration) o;
        return vcpuCount == other.vcpuCount && memoryMib == other.memoryMib;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + (int) vcpuCount;
        result = 31 * result + (int) (memoryMib ^ (memoryMib >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "VirtualMachineConfiguration{"
                + "vcpuCount="
                + vcpuCount
                + ", memoryMib="
                + memoryMib
                + '}';
    }
}
