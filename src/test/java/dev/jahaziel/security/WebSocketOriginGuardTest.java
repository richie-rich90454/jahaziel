/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebSocketOriginGuardTest {

    @Test
    void acceptsOnlyConfiguredOrigin() {
        WebSocketOriginGuard guard = new WebSocketOriginGuard();
        guard.allowOrigin("https://ide.example.com");

        assertTrue(guard.isAllowedOrigin("https://ide.example.com"));
        assertTrue(guard.isAllowedOrigin("https://IDE.EXAMPLE.COM:443"));
        assertFalse(guard.isAllowedOrigin("https://evil.example"));
        assertFalse(guard.isAllowedOrigin("http://ide.example.com"));
        assertFalse(guard.isAllowedOrigin("null"));
        assertFalse(guard.isAllowedOrigin("https://user@ide.example.com"));
        assertFalse(guard.isAllowedOrigin("https://ide.example.com/path"));
    }

    @Test
    void handshakeRequiresBoundWorkspace() {
        WebSocketOriginGuard guard = new WebSocketOriginGuard();
        guard.allowOrigin("https://ide.example.com");
        guard.bind("session-1", "ws-1");

        assertTrue(guard.handshake("session-1", "https://ide.example.com", "ws-1"));
        assertFalse(guard.handshake("session-1", "https://ide.example.com", "ws-2"));
        assertFalse(guard.handshake("session-2", "https://ide.example.com", "ws-1"));
        assertFalse(guard.handshake("session-1", "https://evil.example", "ws-1"));
    }

    @Test
    void unbindRemovesWorkspaceAccess() {
        WebSocketOriginGuard guard = new WebSocketOriginGuard();
        guard.allowOrigin("https://ide.example.com");
        guard.bind("session-1", "ws-1");

        guard.unbind("session-1");

        assertFalse(guard.handshake("session-1", "https://ide.example.com", "ws-1"));
    }
}
