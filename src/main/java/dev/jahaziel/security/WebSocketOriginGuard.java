/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Strict browser-origin check for every WebSocket endpoint. A handshake is accepted only when its
 * Origin matches a configured origin and an authenticated session is bound to the target workspace.
 */
public final class WebSocketOriginGuard {

    private final ArrayList<String> allowedOrigins = new ArrayList<String>();
    private final HashMap<String, String> workspaceBySession = new HashMap<String, String>();

    /** Registers the one canonical form of an allowed browser origin. */
    public void allowOrigin(String origin) {
        String normalized = normalize(origin);
        if (normalized == null) throw new IllegalArgumentException("invalid origin: " + origin);
        if (!allowedOrigins.contains(normalized)) allowedOrigins.add(normalized);
    }

    /** Returns whether a browser Origin matches a configured origin exactly. */
    public boolean isAllowedOrigin(String origin) {
        String normalized = normalize(origin);
        if (normalized == null) return false;
        for (int i = 0; i < allowedOrigins.size(); i++) {
            if (allowedOrigins.get(i).equals(normalized)) return true;
        }
        return false;
    }

    /** Binds an already-authenticated session id to the workspace it may open. */
    public void bind(String sessionId, String workspaceId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("sessionId");
        }
        if (workspaceId == null || workspaceId.isEmpty()) {
            throw new IllegalArgumentException("workspaceId");
        }
        workspaceBySession.put(sessionId, workspaceId);
    }

    /** Removes a session's workspace binding. */
    public void unbind(String sessionId) {
        if (sessionId != null) workspaceBySession.remove(sessionId);
    }

    /** Returns the workspace an authenticated session may open, or null. */
    public String workspace(String sessionId) {
        return workspaceBySession.get(sessionId);
    }

    /**
     * Authenticated handshake gate: both the origin and the session-to-workspace binding must hold.
     */
    public boolean handshake(String sessionId, String origin, String workspaceId) {
        if (!isAllowedOrigin(origin)) return false;
        if (sessionId == null || workspaceId == null) return false;
        String bound = workspaceBySession.get(sessionId);
        return bound != null && bound.equals(workspaceId);
    }

    private static String normalize(String origin) {
        if (origin == null) return null;
        String value = origin.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) return null;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null) return null;
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
            if (uri.getHost() == null || uri.getUserInfo() != null) return null;
            String path = uri.getRawPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) return null;
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            int port = uri.getPort();
            if (port < 0) port = "https".equals(scheme) ? 443 : 80;
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + ":" + port;
        } catch (URISyntaxException malformed) {
            return null;
        }
    }
}
