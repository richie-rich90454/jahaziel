/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

/** Result of starting a session: the id plus the one-time raw token. */
public final class SessionStart {
    private final String sessionId;
    private final String token;

    public SessionStart(String sessionId, String token) {
        this.sessionId = sessionId;
        this.token = token;
    }

    public String sessionId() {
        return sessionId;
    }

    public String token() {
        return token;
    }
}
