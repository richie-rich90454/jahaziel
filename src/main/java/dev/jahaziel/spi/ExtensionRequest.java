/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.Objects;
import java.util.UUID;

/**
 * Request to install an extension.
 *
 * @param extensionId the extension identifier, publisher dot name
 * @param version the requested version
 * @param userId the installing user
 * @param workspaceId the target workspace
 */
public final class ExtensionRequest {

    private final String extensionId;

    private final String version;

    private final UUID userId;

    private final UUID workspaceId;

    public ExtensionRequest(String extensionId, String version, UUID userId, UUID workspaceId) {
        if (extensionId == null || extensionId.isBlank()) {
            throw new IllegalArgumentException("extensionId must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        this.extensionId = extensionId;
        this.version = version;
        this.userId = userId;
        this.workspaceId = workspaceId;
    }

    public String extensionId() {
        return this.extensionId;
    }

    public String getExtensionId() {
        return extensionId;
    }

    public String version() {
        return this.version;
    }

    public String getVersion() {
        return version;
    }

    public UUID userId() {
        return this.userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID workspaceId() {
        return this.workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExtensionRequest)) return false;
        ExtensionRequest other = (ExtensionRequest) o;
        return (java.util.Objects.equals(this.extensionId, other.extensionId))
                && java.util.Objects.equals(this.version, other.version)
                && java.util.Objects.equals(this.userId, other.userId)
                && java.util.Objects.equals(this.workspaceId, other.workspaceId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.extensionId, this.version, this.userId, this.workspaceId);
    }

    @Override
    public String toString() {
        return "ExtensionRequest["
                + "extensionId="
                + this.extensionId
                + ", "
                + "version="
                + this.version
                + ", "
                + "userId="
                + this.userId
                + ", "
                + "workspaceId="
                + this.workspaceId
                + "]";
    }
}
