/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.Objects;
import java.util.UUID;

/**
 * Request to prepare workspace storage and runtime shell.
 *
 * @param workspaceId the workspace to prepare
 * @param ownerId the owning user, keying the persistent home
 * @param image the base image to provision from
 * @param quota the logical quota for the home folder
 * @param vcpuCount virtual CPU count
 * @param memoryMib guest memory in MiB, reserved at start
 */
public final class PrepareRequest {

    private final UUID workspaceId;

    private final UUID ownerId;

    private final ImageRef image;

    private final QuotaBytes quota;

    private final int vcpuCount;

    private final long memoryMib;

    public PrepareRequest(
            UUID workspaceId,
            UUID ownerId,
            ImageRef image,
            QuotaBytes quota,
            int vcpuCount,
            long memoryMib) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(quota, "quota");
        if (vcpuCount < 1) {
            throw new IllegalArgumentException("vcpuCount must be positive");
        }
        if (memoryMib < 1) {
            throw new IllegalArgumentException("memoryMib must be positive");
        }
        this.workspaceId = workspaceId;
        this.ownerId = ownerId;
        this.image = image;
        this.quota = quota;
        this.vcpuCount = vcpuCount;
        this.memoryMib = memoryMib;
    }

    public UUID workspaceId() {
        return this.workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID ownerId() {
        return this.ownerId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public ImageRef image() {
        return this.image;
    }

    public ImageRef getImage() {
        return image;
    }

    public QuotaBytes quota() {
        return this.quota;
    }

    public QuotaBytes getQuota() {
        return quota;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrepareRequest)) return false;
        PrepareRequest other = (PrepareRequest) o;
        return (java.util.Objects.equals(this.workspaceId, other.workspaceId))
                && java.util.Objects.equals(this.ownerId, other.ownerId)
                && java.util.Objects.equals(this.image, other.image)
                && java.util.Objects.equals(this.quota, other.quota)
                && this.vcpuCount == other.vcpuCount
                && this.memoryMib == other.memoryMib;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.workspaceId,
                this.ownerId,
                this.image,
                this.quota,
                this.vcpuCount,
                this.memoryMib);
    }

    @Override
    public String toString() {
        return "PrepareRequest["
                + "workspaceId="
                + this.workspaceId
                + ", "
                + "ownerId="
                + this.ownerId
                + ", "
                + "image="
                + this.image
                + ", "
                + "quota="
                + this.quota
                + ", "
                + "vcpuCount="
                + this.vcpuCount
                + ", "
                + "memoryMib="
                + this.memoryMib
                + "]";
    }
}
