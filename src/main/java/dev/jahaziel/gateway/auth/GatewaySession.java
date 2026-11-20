/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.auth;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A verified gateway session.
 *
 * <p>Components: userId the user identifier, workspaceId the workspace identifier, scopes the
 * granted scopes.
 */
public final class GatewaySession {
    private final UUID userId;
    private final UUID workspaceId;
    private final List<String> scopes;

    public GatewaySession(UUID userId, UUID workspaceId, List<String> scopes) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(scopes, "scopes");
        this.userId = userId;
        this.workspaceId = workspaceId;
        this.scopes = List.copyOf(scopes);
    }

    public UUID userId() {
        return userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public List<String> scopes() {
        return scopes;
    }

    public List<String> getScopes() {
        return scopes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GatewaySession)) return false;
        GatewaySession other = (GatewaySession) o;
        return userId.equals(other.userId)
                && workspaceId.equals(other.workspaceId)
                && scopes.equals(other.scopes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, workspaceId, scopes);
    }

    @Override
    public String toString() {
        return "GatewaySession[userId="
                + userId
                + ", workspaceId="
                + workspaceId
                + ", scopes="
                + scopes
                + "]";
    }
}
