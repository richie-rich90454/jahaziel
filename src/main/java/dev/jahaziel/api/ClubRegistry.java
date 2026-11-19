/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.club.domain.ClubRoles;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.tutorial.TutorialLesson;
import dev.jahaziel.identity.domain.UserId;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory lookup for club sessions and parsed tutorial lessons. */
public final class ClubRegistry {
    private final Map<String, ClubSession> sessions = new HashMap<String, ClubSession>();
    private final Map<String, TutorialLesson> lessons = new HashMap<String, TutorialLesson>();

    public ClubSession createSession(
            UserId host, String template, Instant startsAt, Instant endsAt, int capacity) {
        ClubSession session =
                new ClubSession(
                        UUID.randomUUID().toString(), host, template, startsAt, endsAt, capacity);
        session.join(host, ClubRoles.INSTRUCTOR);
        sessions.put(session.id(), session);
        return session;
    }

    public ClubSession findSession(String id) {
        if (id == null || id.isEmpty()) {
            throw ApiFailureException.badRequest("A session id is required");
        }
        ClubSession session = sessions.get(id);
        if (session == null) {
            throw ApiFailureException.badRequest("Unknown club session");
        }
        return session;
    }

    public void putLesson(TutorialLesson lesson) {
        lessons.put(lesson.id(), lesson);
    }

    public TutorialLesson findLesson(String id) {
        if (id == null || id.isEmpty()) {
            throw ApiFailureException.badRequest("A lesson id is required");
        }
        TutorialLesson lesson = lessons.get(id);
        if (lesson == null) {
            throw ApiFailureException.badRequest("Unknown tutorial lesson");
        }
        return lesson;
    }
}
