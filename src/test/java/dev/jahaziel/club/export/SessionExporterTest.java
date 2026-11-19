/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.club.api.ClubService;
import dev.jahaziel.club.checkpoint.CheckpointService;
import dev.jahaziel.club.domain.ClubRoles;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.session.ClubSessionService;
import dev.jahaziel.club.showcase.ShowcaseGallery;
import dev.jahaziel.club.support.Fakes;
import dev.jahaziel.club.tutorial.ProgressStore;
import dev.jahaziel.club.tutorial.StepVerifier;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Export contents and file output. */
class SessionExporterTest {
    @TempDir Path temp;

    @Test
    void patchContainsSessionMetadataAndSubmissions() throws Exception {
        Fakes.Workspace workspaces = new Fakes.Workspace();
        Fakes.Tasks tasks = new Fakes.Tasks();
        Fakes.Snapshots snapshots = new Fakes.Snapshots();
        Fakes.Authorization authorization = new Fakes.Authorization();
        SessionExporter exporter = new SessionExporter();
        CheckpointService checkpoints =
                new CheckpointService(snapshots, new ShowcaseGallery(authorization), authorization);

        ClubSessionService sessions =
                new ClubSessionService(workspaces, tasks, new NodeId("node-a"));
        UserId host = new UserId(UUID.randomUUID());
        UserId attendee = new UserId(UUID.randomUUID());
        ClubSession session =
                sessions.createSession(
                        host,
                        "java",
                        Instant.parse("2026-11-19T10:00:00Z"),
                        Instant.parse("2026-11-19T12:00:00Z"),
                        10);
        session.join(attendee, ClubRoles.ATTENDEE);
        sessions.forkFor(session, attendee);
        CheckpointService.Submission submission =
                checkpoints.submit(
                        session,
                        attendee,
                        session.workspaceOf(attendee),
                        "checkpoint-1",
                        "final answer",
                        "return 42;\n");

        List<CheckpointService.Submission> submissions =
                new ArrayList<CheckpointService.Submission>();
        submissions.add(submission);
        String patch = exporter.patch(session, submissions);
        assertTrue(patch.contains("session: " + session.id()));
        assertTrue(patch.contains("template: java"));
        assertTrue(patch.contains("source-of-truth: git"));
        assertTrue(patch.contains("note: final answer"));
        assertTrue(patch.contains("return 42;"));
        assertFalse(patch.contains("leaderboard: true"));
        assertTrue(exporter.aggregateRoster(session).contains("capacity=10"));

        Path output = exporter.export(session, submissions, temp.resolve("exports"));
        assertTrue(Files.isRegularFile(output));
        assertEquals(
                patch,
                new String(Files.readAllBytes(output), java.nio.charset.StandardCharsets.UTF_8));

        ClubService club =
                new ClubService(
                        sessions,
                        new StepVerifier(tasks),
                        new ProgressStore(temp.resolve("club")),
                        checkpoints,
                        exporter);
        assertTrue(
                Files.isRegularFile(club.export(session, submissions, temp.resolve("exports2"))));
    }
}
