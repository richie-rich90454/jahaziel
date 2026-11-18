/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.util.HashMap;
import java.util.Map;

/** One append-only collaboration audit record. */
public final class AuditEntry {
    private final String id;
    private final String workspaceId;
    private final String actor;
    private final String action;
    private final String target;
    private final long createdAt;
    private final Map<String, String> details;

    public AuditEntry(
            String id,
            String workspaceId,
            String actor,
            String action,
            String target,
            long createdAt,
            Map<String, String> details) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.createdAt = createdAt;
        this.details = new HashMap<String, String>(details);
    }

    public String id() {
        return id;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String actor() {
        return actor;
    }

    public String action() {
        return action;
    }

    public String target() {
        return target;
    }

    public long createdAt() {
        return createdAt;
    }

    public Map<String, String> details() {
        return new HashMap<String, String>(details);
    }
}
