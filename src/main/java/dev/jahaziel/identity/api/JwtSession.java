/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import dev.jahaziel.identity.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A verified session token.
 *
 * @param user the subject user
 * @param workspaceId the workspace, empty for user-only tokens
 * @param scopes the granted scopes
 * @param expiresAt when the token expires
 */
public final class JwtSession {
    private final UserId user;
    private final Optional<UUID> workspaceId;
    private final List<String> scopes;
    private final Instant expiresAt;

    /** Creates a verified session. */
    public JwtSession(
            UserId user, Optional<UUID> workspaceId, List<String> scopes, Instant expiresAt) {
        this.user = Objects.requireNonNull(user, "user");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes"));
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** Returns the subject user. */
    public UserId user() {
        return user;
    }

    public UserId getUser() {
        return user;
    }

    /** Returns the workspace, empty for user-only tokens. */
    public Optional<UUID> workspaceId() {
        return workspaceId;
    }

    public Optional<UUID> getWorkspaceId() {
        return workspaceId;
    }

    /** Returns the granted scopes. */
    public List<String> scopes() {
        return scopes;
    }

    public List<String> getScopes() {
        return scopes;
    }

    /** Returns the expiry instant. */
    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** Compares by class and all fields. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JwtSession that = (JwtSession) o;
        return user.equals(that.user)
                && workspaceId.equals(that.workspaceId)
                && scopes.equals(that.scopes)
                && expiresAt.equals(that.expiresAt);
    }

    /** Returns the hash code derived from all fields. */
    @Override
    public int hashCode() {
        return Objects.hash(user, workspaceId, scopes, expiresAt);
    }

    /** Returns a readable representation. */
    @Override
    public String toString() {
        return "JwtSession[user="
                + user
                + ", workspaceId="
                + workspaceId
                + ", scopes="
                + scopes
                + ", expiresAt="
                + expiresAt
                + "]";
    }
}
