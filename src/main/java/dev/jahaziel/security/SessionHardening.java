/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 * In-memory session indexes with rotation on privilege change, idle expiry, and an admin revoke
 * list. All state is process-local; nothing is written outside the application.
 */
public final class SessionHardening {

    private final long idleTimeoutMillis;
    private final HashMap<String, String> userBySession = new HashMap<String, String>();
    private final HashMap<String, String> roleBySession = new HashMap<String, String>();
    private final HashMap<String, Long> lastSeenBySession = new HashMap<String, Long>();
    private final HashMap<String, Long> revokedAtBySession = new HashMap<String, Long>();

    /** Creates the registry with an idle timeout in milliseconds. */
    public SessionHardening(long idleTimeoutMillis) {
        if (idleTimeoutMillis <= 0) {
            throw new IllegalArgumentException("idleTimeoutMillis must be positive");
        }
        this.idleTimeoutMillis = idleTimeoutMillis;
    }

    /** Issues a fresh session id for a user and role. */
    public String issue(String userId, String role, long now) {
        if (userId == null || userId.isEmpty()) throw new IllegalArgumentException("userId");
        String id = UUID.randomUUID().toString();
        userBySession.put(id, userId);
        roleBySession.put(id, role == null ? "" : role);
        lastSeenBySession.put(id, Long.valueOf(now));
        return id;
    }

    /** Validates and touches a session, applying both revocation and idle expiry. */
    public boolean valid(String sessionId, long now) {
        if (sessionId == null || revokedAtBySession.containsKey(sessionId)) return false;
        Long last = lastSeenBySession.get(sessionId);
        if (last == null) return false;
        if (now - last.longValue() > idleTimeoutMillis) return false;
        lastSeenBySession.put(sessionId, Long.valueOf(now));
        return true;
    }

    /**
     * Rotates the session id when the role changes. The old id is revoked so a stolen pre-escalation
     * id cannot ride the new privilege.
     */
    public String rotate(String sessionId, String newRole, long now) {
        String currentRole = roleBySession.get(sessionId);
        String role = newRole == null ? "" : newRole;
        if (currentRole != null && currentRole.equals(role)) return sessionId;
        String user = userBySession.get(sessionId);
        if (user == null) return null;
        revoke(sessionId, now);
        return issue(user, role, now);
    }

    /** Adds a session to the admin revoke list and drops its live indexes. */
    public void revoke(String sessionId, long now) {
        if (sessionId == null) return;
        revokedAtBySession.put(sessionId, Long.valueOf(now));
        userBySession.remove(sessionId);
        roleBySession.remove(sessionId);
        lastSeenBySession.remove(sessionId);
    }

    /** Returns whether an id was revoked. */
    public boolean revoked(String sessionId) {
        return sessionId != null && revokedAtBySession.containsKey(sessionId);
    }

    /** Returns the user bound to a live session, or null. */
    public String user(String sessionId) {
        return userBySession.get(sessionId);
    }

    /** Returns the role bound to a live session, or null. */
    public String role(String sessionId) {
        return roleBySession.get(sessionId);
    }

    /** Returns the configured idle timeout. */
    public long idleTimeoutMillis() {
        return idleTimeoutMillis;
    }

    /** Returns a copy of the admin revoke list. */
    public ArrayList<String> revokedSessions() {
        return new ArrayList<String>(revokedAtBySession.keySet());
    }
}
