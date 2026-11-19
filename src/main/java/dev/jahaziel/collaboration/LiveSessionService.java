/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single-controller, multi-observer live sessions. Observers must give explicit consent. The
 * controller token is a hash in the store and is revoked when the controller disconnects.
 */
@Service
public final class LiveSessionService {
    private static final String TOKEN_PREFIX = "jls_";

    private final CollaborationStore store;
    private final CollaborationAudit audit;
    private final MembershipService membership;

    public LiveSessionService(
            CollaborationStore store, CollaborationAudit audit, MembershipService membership) {
        this.store = store;
        this.audit = audit;
        this.membership = membership;
    }

    public SessionStart create(String workspaceId, String controller, long nowMillis) {
        membership.requireRole(workspaceId, controller, Roles.EDITOR);
        String token = Tokens.generate(TOKEN_PREFIX);
        LiveSession session =
                new LiveSession(
                        UUID.randomUUID().toString(),
                        workspaceId,
                        controller,
                        Tokens.hash(token),
                        nowMillis);
        store.putSession(session);
        audit.record(
                workspaceId,
                controller,
                "session.create",
                session.id(),
                new HashMap<String, String>());
        return new SessionStart(session.id(), token);
    }

    /** An observer joins only with explicit consent and an active controller. */
    public void joinAsObserver(String sessionId, String observer, boolean consent, long nowMillis) {
        LiveSession session = requireSession(sessionId);
        if (!session.active()) {
            throw new IllegalArgumentException("session is no longer active");
        }
        if (!consent) {
            throw new CollaborationAccessDeniedException("observer consent is required");
        }
        membership.requireRole(session.workspaceId(), observer, Roles.VIEWER);
        if (session.controller().equals(observer)) {
            throw new IllegalArgumentException("the controller cannot be an observer");
        }
        session.addObserver(observer);
        store.putSession(session);
        Map<String, String> details = new HashMap<String, String>();
        details.put("session", sessionId);
        audit.record(session.workspaceId(), observer, "session.observe", observer, details);
    }

    public void leaveObserver(String sessionId, String observer) {
        LiveSession session = requireSession(sessionId);
        session.removeObserver(observer);
        store.putSession(session);
        audit.record(
                session.workspaceId(),
                observer,
                "session.leave",
                observer,
                new HashMap<String, String>());
    }

    /** Revokes the controller token by deactivating the session. */
    public void disconnectController(String sessionId, String actor) {
        LiveSession session = requireSession(sessionId);
        if (!session.controller().equals(actor)) {
            throw new CollaborationAccessDeniedException("only the controller may disconnect");
        }
        session.active(false);
        store.putSession(session);
        audit.record(
                session.workspaceId(),
                actor,
                "session.disconnect",
                sessionId,
                new HashMap<String, String>());
    }

    /** Returns the active session for a raw token, or null when unknown or revoked. */
    public LiveSession verify(String token) {
        if (token == null || token.length() == 0) {
            return null;
        }
        LiveSession session = store.sessionByHash(Tokens.hash(token));
        return session == null || !session.active() ? null : session;
    }

    public List<String> observers(String sessionId) {
        return requireSession(sessionId).observers();
    }

    private LiveSession requireSession(String sessionId) {
        LiveSession session = store.session(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("unknown live session");
        }
        return session;
    }
}
