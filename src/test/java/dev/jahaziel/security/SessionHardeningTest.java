/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionHardeningTest {

    @Test
    void rotatesOnPrivilegeChangeAndRevokesOldId() {
        SessionHardening sessions = new SessionHardening(1000L);
        String original = sessions.issue("user-1", "user", 0L);
        assertTrue(sessions.valid(original, 100L));

        String rotated = sessions.rotate(original, "admin", 200L);

        assertNotEquals(original, rotated);
        assertTrue(sessions.revoked(original));
        assertFalse(sessions.valid(original, 201L));
        assertTrue(sessions.valid(rotated, 201L));
        assertEquals("admin", sessions.role(rotated));
        assertEquals("user-1", sessions.user(rotated));
    }

    @Test
    void sameRoleDoesNotRotate() {
        SessionHardening sessions = new SessionHardening(1000L);
        String original = sessions.issue("user-1", "user", 0L);

        assertEquals(original, sessions.rotate(original, "user", 10L));
        assertFalse(sessions.revoked(original));
    }

    @Test
    void idleTimeoutExpiresSession() {
        SessionHardening sessions = new SessionHardening(1000L);
        String id = sessions.issue("user-1", "user", 0L);

        assertTrue(sessions.valid(id, 1000L));
        assertFalse(sessions.valid(id, 2001L));
    }

    @Test
    void adminRevokeBlocksValidation() {
        SessionHardening sessions = new SessionHardening(1000L);
        String id = sessions.issue("user-1", "user", 0L);

        sessions.revoke(id, 10L);

        assertTrue(sessions.revoked(id));
        assertFalse(sessions.valid(id, 11L));
        assertEquals(1, sessions.revokedSessions().size());
    }
}
