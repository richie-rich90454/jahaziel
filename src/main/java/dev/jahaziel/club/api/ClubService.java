/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.api;

import dev.jahaziel.club.checkpoint.CheckpointService;
import dev.jahaziel.club.checkpoint.CheckpointService.Submission;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.export.SessionExporter;
import dev.jahaziel.club.session.ClubSessionService;
import dev.jahaziel.club.showcase.ShowcaseGallery;
import dev.jahaziel.club.tutorial.ProgressStore;
import dev.jahaziel.club.tutorial.StepVerifier;
import dev.jahaziel.club.tutorial.TutorialLesson.Step;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.util.List;
import java.util.Objects;

/**
 * Application facade for a Developer Club session.
 *
 * <p>Progress is aggregate by default. Per-person completion is exposed only when the session
 * explicitly enables a leaderboard, and every verification and submission is rate limited.
 */
public final class ClubService {
    private static final int MAX_VERIFY_PER_SESSION = 20;
    private static final int MAX_SUBMISSIONS_PER_SESSION = 5;

    private final ClubSessionService sessions;
    private final StepVerifier verifier;
    private final ProgressStore progress;
    private final CheckpointService checkpoints;
    private final SessionExporter exporter;

    /**
     * Creates the facade.
     *
     * @param sessions the session and workspace service
     * @param verifier the step verifier
     * @param progress the progress store
     * @param checkpoints the checkpoint service
     * @param exporter the session exporter
     */
    public ClubService(
            ClubSessionService sessions,
            StepVerifier verifier,
            ProgressStore progress,
            CheckpointService checkpoints,
            SessionExporter exporter) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.checkpoints = Objects.requireNonNull(checkpoints, "checkpoints");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
    }

    /**
     * Verifies a lesson step and records aggregate progress on success.
     *
     * @param session the session
     * @param user the attendee
     * @param lessonId the lesson identifier
     * @param step the step
     * @return the bounded result
     */
    public StepResult verifyStep(ClubSession session, UserId user, String lessonId, Step step) {
        requireWriter(session, user);
        if (!session.tryAction(
                user, "verify:" + lessonId + ":" + step.id(), MAX_VERIFY_PER_SESSION)) {
            throw new ClubLimitException("step verification rate limit reached");
        }
        WorkspaceId workspace = session.workspaceOf(user);
        if (workspace == null) {
            throw new ClubAccessDeniedException("user has no isolated club workspace");
        }
        StepVerifier.Verification verification = verifier.verify(workspace, step);
        if (verification.passed()) {
            progress.markCompleted(user, lessonId, step.id());
        }
        int aggregate = progress.aggregateCompleted(lessonId, step.id());
        int personal = -1;
        if (session.isLeaderboardEnabled()) {
            personal = progress.completedForUser(user, lessonId);
        }
        return new StepResult(verification.passed(), verification.detail(), aggregate, personal);
    }

    /**
     * Returns aggregate completions for a step. Available without a leaderboard.
     *
     * @param lessonId the lesson identifier
     * @param stepId the step identifier
     * @return the number of users who completed the step
     */
    public int aggregateCompleted(String lessonId, String stepId) {
        return progress.aggregateCompleted(lessonId, stepId);
    }

    /**
     * Returns one user's completion count. Only available when a leaderboard is explicitly enabled.
     *
     * @param session the session
     * @param user the user
     * @param lessonId the lesson identifier
     * @return the personal completion count
     */
    public int personalCompleted(ClubSession session, UserId user, String lessonId) {
        if (!session.isLeaderboardEnabled()) {
            throw new ClubAccessDeniedException("leaderboard is not enabled for this session");
        }
        return progress.completedForUser(user, lessonId);
    }

    /**
     * Submits a checkpoint after a rate limit check.
     *
     * @param session the session
     * @param user the attendee
     * @param workspace the isolated workspace
     * @param tag the snapshot tag
     * @param note the note
     * @param content the submitted text
     * @return the submission
     */
    public Submission submitCheckpoint(
            ClubSession session,
            UserId user,
            WorkspaceId workspace,
            String tag,
            String note,
            String content) {
        requireWriter(session, user);
        if (!session.tryAction(user, "submit", MAX_SUBMISSIONS_PER_SESSION)) {
            throw new ClubLimitException("checkpoint submission rate limit reached");
        }
        return checkpoints.submit(session, user, workspace, tag, note, content);
    }

    /** Returns the session service. */
    public ClubSessionService sessions() {
        return sessions;
    }

    /** Returns the curated showcase gallery. */
    public ShowcaseGallery showcase() {
        return checkpoints.gallery();
    }

    /** Returns the session exporter. */
    public SessionExporter exporter() {
        return exporter;
    }

    /** Exports a session and its submissions. */
    public java.nio.file.Path export(
            ClubSession session, List<Submission> submissions, java.nio.file.Path outputDir) {
        return exporter.export(session, submissions, outputDir);
    }

    private static void requireWriter(ClubSession session, UserId user) {
        if (!session.mayWrite(user)) {
            throw new ClubAccessDeniedException("user may not write in this session");
        }
    }

    /** Bounded result of one guided step verification. */
    public static final class StepResult {
        private final boolean passed;
        private final String detail;
        private final int aggregateCompleted;
        private final int personalCompleted;

        /**
         * Creates a result.
         *
         * @param passed whether the step passed
         * @param detail the short reason
         * @param aggregateCompleted aggregate completions, always available
         * @param personalCompleted personal completions, or -1 when no leaderboard is enabled
         */
        public StepResult(
                boolean passed, String detail, int aggregateCompleted, int personalCompleted) {
            this.passed = passed;
            this.detail = detail;
            this.aggregateCompleted = aggregateCompleted;
            this.personalCompleted = personalCompleted;
        }

        /** Returns whether the step passed. */
        public boolean passed() {
            return passed;
        }

        /** Returns the short reason. */
        public String detail() {
            return detail;
        }

        /** Returns aggregate completions. */
        public int aggregateCompleted() {
            return aggregateCompleted;
        }

        /** Returns personal completions, or -1 when the leaderboard is disabled. */
        public int personalCompleted() {
            return personalCompleted;
        }
    }
}
