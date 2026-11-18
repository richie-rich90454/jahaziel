/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

/** A redeemable share link. Only the token hash is persisted. */
public final class ShareLink {
    private final String id;
    private final String workspaceId;
    private final String tokenHash;
    private final String scope;
    private final boolean oneTime;
    private final long expiresAt;
    private final String createdBy;
    private final long createdAt;
    private boolean used;
    private boolean revoked;

    public ShareLink(
            String id,
            String workspaceId,
            String tokenHash,
            String scope,
            boolean oneTime,
            long expiresAt,
            String createdBy,
            long createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.tokenHash = tokenHash;
        this.scope = scope;
        this.oneTime = oneTime;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public String scope() {
        return scope;
    }

    public boolean oneTime() {
        return oneTime;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean used() {
        return used;
    }

    public void used(boolean used) {
        this.used = used;
    }

    public boolean revoked() {
        return revoked;
    }

    public void revoked(boolean revoked) {
        this.revoked = revoked;
    }
}
