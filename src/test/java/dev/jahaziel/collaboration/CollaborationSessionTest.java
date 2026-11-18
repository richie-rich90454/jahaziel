/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class CollaborationSessionTest {
    @Test
    void observersRequireExplicitConsent() {
        CollaborationFixture fixture = new CollaborationFixture(1000L);
        SessionStart started =
                fixture.sessions.create(
                        CollaborationFixture.WORKSPACE, CollaborationFixture.OWNER, 1000L);
        assertNotNull(started.token());
        assertNotNull(fixture.sessions.verify(started.token()));

        assertThrows(
                CollaborationAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.sessions.joinAsObserver(
                                started.sessionId(), CollaborationFixture.VIEWER, false, 2000L);
                    }
                });
        fixture.sessions.joinAsObserver(
                started.sessionId(), CollaborationFixture.VIEWER, true, 2000L);
        assertTrue(fixture.sessions.observers(started.sessionId()).contains(CollaborationFixture.VIEWER));
    }

    @Test
    void nonMemberCannotObserve() {
        final CollaborationFixture fixture = new CollaborationFixture(1000L);
        final SessionStart started =
                fixture.sessions.create(
                        CollaborationFixture.WORKSPACE, CollaborationFixture.OWNER, 1000L);
        assertThrows(
                CollaborationAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.sessions.joinAsObserver(
                                started.sessionId(), CollaborationFixture.STRANGER, true, 2000L);
                    }
                });
    }

    @Test
    void controllerDisconnectRevokesToken() {
        CollaborationFixture fixture = new CollaborationFixture(1000L);
        SessionStart started =
                fixture.sessions.create(
                        CollaborationFixture.WORKSPACE, CollaborationFixture.OWNER, 1000L);
        fixture.sessions.disconnectController(started.sessionId(), CollaborationFixture.OWNER);
        assertNull(fixture.sessions.verify(started.token()));
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.sessions.joinAsObserver(
                                started.sessionId(), CollaborationFixture.VIEWER, true, 2000L);
                    }
                });
    }

    @Test
    void onlyControllerDisconnects() {
        final CollaborationFixture fixture = new CollaborationFixture(1000L);
        final SessionStart started =
                fixture.sessions.create(
                        CollaborationFixture.WORKSPACE, CollaborationFixture.OWNER, 1000L);
        assertThrows(
                CollaborationAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.sessions.disconnectController(
                                started.sessionId(), CollaborationFixture.EDITOR);
                    }
                });
        assertEquals(1, fixture.store.audit(CollaborationFixture.WORKSPACE).size());
    }
}
