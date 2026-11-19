/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.club.api.ClubAccessDeniedException;
import dev.jahaziel.club.api.ClubLimitException;
import dev.jahaziel.club.domain.ClubRoles;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.support.Fakes;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.workspace.domain.Workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.UUID;

/** Role, capacity, forking, and demo-controller behavior. */
class ClubSessionServiceTest {
    private Fakes.Workspace workspaces;
    private Fakes.Tasks tasks;
    private ClubSessionService service;
    private UserId host;
    private ClubSession session;
    private Instant start;
    private Instant end;

    @BeforeEach
    void setUp() {
        workspaces = new Fakes.Workspace();
        tasks = new Fakes.Tasks();
        service = new ClubSessionService(workspaces, tasks, new NodeId("node-a"));
        host = new UserId(UUID.randomUUID());
        start = Instant.parse("2026-11-19T10:00:00Z");
        end = Instant.parse("2026-11-19T12:00:00Z");
        session = service.createSession(host, "java", start, end, 10);
    }

    @Test
    void registersHostAsInstructorAndRejectsUnknownRoles() {
        assertEquals(ClubRoles.INSTRUCTOR, session.roleOf(host));
        assertEquals("java", session.template());
        assertEquals(1, session.size());
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        session.join(new UserId(UUID.randomUUID()), "ADMIN");
                    }
                });
    }

    @Test
    void forksIsolatedWorkspacesFromTheSessionTemplate() {
        UserId first = new UserId(UUID.randomUUID());
        UserId second = new UserId(UUID.randomUUID());
        session.join(first, ClubRoles.ATTENDEE);
        session.join(second, ClubRoles.ATTENDEE);

        Workspace one = service.forkFor(session, first);
        Workspace two = service.forkFor(session, second);

        assertFalse(one.id().equals(two.id()));
        assertEquals(first, one.owner());
        assertEquals(second, two.owner());
        assertEquals(2, tasks.initializedTemplates.size());
        assertEquals("java", tasks.initializedTemplates.get(0));
        assertEquals(one.id(), tasks.initializedIds.get(0));
        assertEquals(one.id(), session.workspaceOf(first));
    }

    @Test
    void observersDoNotReceiveAFork() {
        UserId observer = new UserId(UUID.randomUUID());
        session.join(observer, ClubRoles.OBSERVER);

        assertThrows(
                ClubAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.forkFor(session, observer);
                    }
                });
        assertEquals(0, tasks.initializedIds.size());
    }

    @Test
    void enforcesCapacityAndLockState() {
        ClubSession tiny = new ClubSession("tiny", host, "java", start, end, 1);
        tiny.join(new UserId(UUID.randomUUID()), ClubRoles.ATTENDEE);
        assertThrows(
                ClubLimitException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        tiny.join(new UserId(UUID.randomUUID()), ClubRoles.ATTENDEE);
                    }
                });

        tiny.lock();
        assertThrows(
                ClubLimitException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        tiny.join(new UserId(UUID.randomUUID()), ClubRoles.ATTENDEE);
                    }
                });
    }

    @Test
    void sharedDemoHasASingleControllerAndReadOnlyFollowers() {
        UserId attendee = new UserId(UUID.randomUUID());
        UserId observer = new UserId(UUID.randomUUID());
        session.join(attendee, ClubRoles.ATTENDEE);
        session.join(observer, ClubRoles.OBSERVER);

        Workspace demo = service.createDemo(session);
        assertTrue(session.isDemoEnabled());
        assertEquals(demo.id(), service.followDemo(session, observer));
        service.controlDemo(session, host);

        assertThrows(
                ClubAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.controlDemo(session, attendee);
                    }
                });
        assertThrows(
                ClubAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.controlDemo(session, observer);
                    }
                });

        service.transferDemoControl(session, host, attendee);
        assertEquals(attendee, session.demoController());
        service.controlDemo(session, attendee);
        assertNotNull(demo.id());
    }

    @Test
    void joinCodeRotationAndRateCountersBehave() {
        String original = session.joinCode();
        String rotated = session.rotateJoinCode();
        assertFalse(original.equals(rotated));
        assertTrue(rotated.length() == 12);

        assertTrue(session.tryAction(host, "verify", 2));
        assertTrue(session.tryAction(host, "verify", 2));
        assertFalse(session.tryAction(host, "verify", 2));
        session.resetRateLimits();
        assertTrue(session.tryAction(host, "verify", 2));
    }
}
