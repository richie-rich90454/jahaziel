/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a workspace. Sizing is optional and defaults apply when absent.
 *
 * @param name the workspace name, unique per owner
 * @param memoryMib guest memory in MiB, empty for the default
 * @param vcpuCount virtual CPU count, empty for the default
 */
public final class CreateWorkspaceRequest {

    @NotBlank
    @Size(max = 64)
    private final String name;

    @Min(1024)
    @Max(4096)
    private final Integer memoryMib;

    @Min(1)
    @Max(4)
    private final Integer vcpuCount;

    public CreateWorkspaceRequest(
            @NotBlank @Size(max = 64) String name,
            @Min(1024) @Max(4096) Integer memoryMib,
            @Min(1) @Max(4) Integer vcpuCount) {
        this.name = name;
        this.memoryMib = memoryMib;
        this.vcpuCount = vcpuCount;
    }

    public String name() {
        return this.name;
    }

    public String getName() {
        return this.name;
    }

    public Integer memoryMib() {
        return this.memoryMib;
    }

    public Integer getMemoryMib() {
        return this.memoryMib;
    }

    public Integer vcpuCount() {
        return this.vcpuCount;
    }

    public Integer getVcpuCount() {
        return this.vcpuCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreateWorkspaceRequest)) return false;
        CreateWorkspaceRequest other = (CreateWorkspaceRequest) o;
        return (java.util.Objects.equals(this.name, other.name))
                && java.util.Objects.equals(this.memoryMib, other.memoryMib)
                && java.util.Objects.equals(this.vcpuCount, other.vcpuCount);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.name, this.memoryMib, this.vcpuCount);
    }

    @Override
    public String toString() {
        return "CreateWorkspaceRequest["
                + "name="
                + this.name
                + ", "
                + "memoryMib="
                + this.memoryMib
                + ", "
                + "vcpuCount="
                + this.vcpuCount
                + "]";
    }
}
