/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

/** One pending or accepted workspace invitation. The id doubles as the accept token. */
public final class Invitation {
    private final String id;
    private final String workspaceId;
    private final String invitee;
    private final String role;
    private final String invitedBy;
    private final long createdAt;
    private final long expiresAt;
    private boolean accepted;
    private long acceptedAt;

    public Invitation(
            String id,
            String workspaceId,
            String invitee,
            String role,
            String invitedBy,
            long createdAt,
            long expiresAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.invitee = invitee;
        this.role = role;
        this.invitedBy = invitedBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String id() {
        return id;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String invitee() {
        return invitee;
    }

    public String role() {
        return role;
    }

    public String invitedBy() {
        return invitedBy;
    }

    public long createdAt() {
        return createdAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean accepted() {
        return accepted;
    }

    public void accepted(boolean accepted) {
        this.accepted = accepted;
    }

    public long acceptedAt() {
        return acceptedAt;
    }

    public void acceptedAt(long acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}
