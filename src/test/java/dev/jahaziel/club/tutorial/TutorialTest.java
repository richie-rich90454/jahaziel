/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.tutorial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.club.api.ClubAccessDeniedException;
import dev.jahaziel.club.api.ClubLimitException;
import dev.jahaziel.club.api.ClubService;
import dev.jahaziel.club.checkpoint.CheckpointService;
import dev.jahaziel.club.domain.ClubRoles;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.export.SessionExporter;
import dev.jahaziel.club.session.ClubSessionService;
import dev.jahaziel.club.showcase.ShowcaseGallery;
import dev.jahaziel.club.support.Fakes;
import dev.jahaziel.club.tutorial.TutorialLesson.Step;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/** Markdown parsing, task-based step verification, and aggregate progress. */
class TutorialTest {
    @TempDir Path temp;

    private Fakes.Tasks tasks;
    private Fakes.Workspace workspaces;
    private TutorialLesson lesson;
    private Step step;
    private UserId user;
    private ClubSession session;
    private ProgressStore progress;

    @BeforeEach
    void setUp() {
        tasks = new Fakes.Tasks();
        tasks.outcome("SUCCEEDED", 0, "hello");
        String markdown =
                "---\n"
                        + "id: basics\n"
                        + "title: Java Basics\n"
                        + "objective: Compile a program\n"
                        + "---\n"
                        + "## step-1\n"
                        + "title: Hello world\n"
                        + "objective: Print a greeting\n"
                        + "hint: Use println\n"
                        + "solution_diff: +System.out.println(\"hello\");\n"
                        + "validation_command: task:check-1\n"
                        + "expected_output: hello\n";
        lesson = new TutorialParser().parse(markdown);
        step = lesson.step("step-1");
        user = new UserId(UUID.randomUUID());
        progress = new ProgressStore(temp.resolve("club"));
        workspaces = new Fakes.Workspace();
        ClubSessionService sessions =
                new ClubSessionService(workspaces, tasks, new NodeId("node-a"));
        session =
                sessions.createSession(
                        new UserId(UUID.randomUUID()),
                        "java",
                        Instant.parse("2026-11-19T10:00:00Z"),
                        Instant.parse("2026-11-19T12:00:00Z"),
                        10);
        session.join(user, ClubRoles.ATTENDEE);
        sessions.forkFor(session, user);
    }

    private ClubService facade(ClubSessionService sessions, StepVerifier verifier) {
        Fakes.Snapshots snapshots = new Fakes.Snapshots();
        Fakes.Authorization authorization = new Fakes.Authorization();
        CheckpointService checkpoints =
                new CheckpointService(snapshots, new ShowcaseGallery(authorization), authorization);
        return new ClubService(sessions, verifier, progress, checkpoints, new SessionExporter());
    }

    @Test
    void parsesStepFrontmatter() {
        assertEquals("basics", lesson.id());
        assertEquals("Java Basics", lesson.title());
        assertEquals(1, lesson.steps().size());
        assertEquals("Print a greeting", step.objective());
        assertEquals("Use println", step.hint());
        assertTrue(step.solutionDiff().contains("println"));
        assertEquals("check-1", step.taskId());
        assertEquals("hello", step.expectedOutput());
    }

    @Test
    void verifiesExitStatusAndExpectedOutput() {
        StepVerifier verifier = new StepVerifier(tasks);
        StepVerifier.Verification passed = verifier.verify(session.workspaceOf(user), step);
        assertTrue(passed.passed());
        assertTrue(tasks.startedTasks.contains("check-1"));

        tasks.outcome("FAILED", 1, "no output");
        StepVerifier.Verification failed = verifier.verify(session.workspaceOf(user), step);
        assertFalse(failed.passed());

        tasks.outcome("SUCCEEDED", 0, "something else");
        assertFalse(verifier.verify(session.workspaceOf(user), step).passed());
    }

    @Test
    void progressStoreDeduplicatesAndCountsAggregates() {
        UserId other = new UserId(UUID.randomUUID());
        progress.markCompleted(user, "basics", "step-1");
        progress.markCompleted(user, "basics", "step-1");
        progress.markCompleted(other, "basics", "step-1");

        assertTrue(progress.isCompleted(user, "basics", "step-1"));
        assertEquals(2, progress.aggregateCompleted("basics", "step-1"));
        assertEquals(1, progress.completedForUser(user, "basics"));
    }

    @Test
    void facadeExposesAggregateOnlyUnlessLeaderboardIsEnabled() {
        Fakes.Workspace workspaces = new Fakes.Workspace();
        ClubSessionService sessions =
                new ClubSessionService(workspaces, tasks, new NodeId("node-a"));
        sessions.forkFor(session, user);
        ClubService club = facade(sessions, new StepVerifier(tasks));

        ClubService.StepResult result = club.verifyStep(session, user, "basics", step);
        assertTrue(result.passed());
        assertEquals(1, result.aggregateCompleted());
        assertEquals(-1, result.personalCompleted());

        assertThrows(
                ClubAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        club.personalCompleted(session, user, "basics");
                    }
                });

        session.setLeaderboardEnabled(true);
        assertEquals(1, club.personalCompleted(session, user, "basics"));
    }

    @Test
    void facadeRateLimitsRepeatedVerification() {
        Fakes.Workspace workspaces = new Fakes.Workspace();
        ClubSessionService sessions =
                new ClubSessionService(workspaces, tasks, new NodeId("node-a"));
        sessions.forkFor(session, user);
        final ClubService club = facade(sessions, new StepVerifier(tasks));

        for (int index = 0; index < 20; index++) {
            club.verifyStep(session, user, "basics", step);
        }
        assertThrows(
                ClubLimitException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        club.verifyStep(session, user, "basics", step);
                    }
                });
    }
}
