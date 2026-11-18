/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

/** One workspace membership row. */
public final class Member {
    private final String workspaceId;
    private final String userId;
    private String role;
    private final long joinedAt;

    public Member(String workspaceId, String userId, String role, long joinedAt) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String userId() {
        return userId;
    }

    public String role() {
        return role;
    }

    public void role(String role) {
        this.role = role;
    }

    public long joinedAt() {
        return joinedAt;
    }
}
