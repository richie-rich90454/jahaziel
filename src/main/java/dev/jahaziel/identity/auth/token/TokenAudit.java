/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.token;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory audit trail plus an optional listener hook. Detail is always a token id, never a token.
 */
public final class TokenAudit {
    private final List<String> events = new ArrayList<String>();
    private AuditListener listener;

    public synchronized void setListener(AuditListener value) {
        this.listener = value;
    }

    public synchronized void record(String event, String detail) {
        events.add(event + ":" + detail);
        AuditListener current = listener;
        if (current != null) {
            current.onEvent(event, detail);
        }
    }

    public synchronized List<String> events() {
        return new ArrayList<String>(events);
    }
}
