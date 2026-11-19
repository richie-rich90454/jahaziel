/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

final class CollaborationPresenceTest {
    @Test
    void heartbeatAndExpiryWindows() {
        assertEquals(30000L, PresenceService.HEARTBEAT_MS);
        assertEquals(90000L, PresenceService.EXPIRY_MS);
    }

    @Test
    void eventsRefreshHeartbeatAndExpireAfterNinetySeconds() {
        PresenceService presence = new PresenceService();
        presence.join(CollaborationFixture.WORKSPACE, CollaborationFixture.EDITOR, 1000L);
        presence.activeFile(
                CollaborationFixture.WORKSPACE,
                CollaborationFixture.EDITOR,
                "src/Main.java",
                2000L);
        PresenceService.Presence cursor =
                presence.cursor(
                        CollaborationFixture.WORKSPACE, CollaborationFixture.EDITOR, 12, 4, 3000L);
        assertEquals("src/Main.java", cursor.activeFile());
        assertEquals(12, cursor.line());
        assertEquals(4, cursor.column());
        assertEquals(3000L, cursor.lastHeartbeat());

        presence.heartbeat(CollaborationFixture.WORKSPACE, CollaborationFixture.EDITOR, 33000L);
        List<PresenceService.Presence> stillActive =
                presence.active(CollaborationFixture.WORKSPACE, 123000L);
        assertEquals(1, stillActive.size());

        assertEquals(1, presence.expire(123001L));
        assertTrue(presence.active(CollaborationFixture.WORKSPACE, 123001L).isEmpty());
    }

    @Test
    void leaveRemovesEntryImmediately() {
        PresenceService presence = new PresenceService();
        presence.join(CollaborationFixture.WORKSPACE, CollaborationFixture.VIEWER, 1000L);
        presence.leave(CollaborationFixture.WORKSPACE, CollaborationFixture.VIEWER);
        assertTrue(presence.active(CollaborationFixture.WORKSPACE, 1000L).isEmpty());
    }

    @Test
    void presenceIsAdvisoryOnly() {
        PresenceService presence = new PresenceService();
        presence.join(CollaborationFixture.WORKSPACE, CollaborationFixture.STRANGER, 1000L);
        assertEquals(1, presence.active(CollaborationFixture.WORKSPACE, 1000L).size());
        assertNull(fixtureRoleOfStranger());
    }

    private static String fixtureRoleOfStranger() {
        CollaborationFixture fixture = new CollaborationFixture(1000L);
        return fixture.membership.roleOf(
                CollaborationFixture.WORKSPACE, CollaborationFixture.STRANGER);
    }
}
