/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Writes one audit entry for every collaboration mutation. */
public final class CollaborationAudit {
    private final CollaborationStore store;

    public CollaborationAudit(CollaborationStore store) {
        this.store = store;
    }

    public void record(
            String workspaceId,
            String actor,
            String action,
            String target,
            Map<String, String> details) {
        Map<String, String> copy = new HashMap<String, String>();
        if (details != null) {
            copy.putAll(details);
        }
        store.appendAudit(
                new AuditEntry(
                        UUID.randomUUID().toString(),
                        workspaceId,
                        actor,
                        action,
                        target,
                        System.currentTimeMillis(),
                        copy));
    }
}
