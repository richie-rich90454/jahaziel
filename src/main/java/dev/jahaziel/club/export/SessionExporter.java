/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.export;

import dev.jahaziel.club.checkpoint.CheckpointService.Submission;
import dev.jahaziel.club.domain.ClubSession;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Exports a club session and its checkpoint submissions as an advisory patch file.
 *
 * <p><b>Club is additive and git remains the source of truth.</b> An export never replaces a
 * participant's repository: it is a readable patch plus manifest that an instructor may hand off
 * for review. No download endpoint is provided by the showcase, and no export writes into a
 * workspace.
 */
public final class SessionExporter {

    /**
     * Builds the patch text for a session and its submissions.
     *
     * @param session the session
     * @param submissions the checkpoint submissions to include
     * @return the bounded patch text
     */
    public String patch(ClubSession session, List<Submission> submissions) {
        Objects.requireNonNull(session, "session");
        StringBuilder text = new StringBuilder();
        text.append("# club session export\n");
        text.append("session: ").append(session.id()).append('\n');
        text.append("template: ").append(session.template()).append('\n');
        text.append("starts: ").append(session.startsAt().toString()).append('\n');
        text.append("ends: ").append(session.endsAt().toString()).append('\n');
        text.append("capacity: ").append(session.capacity()).append('\n');
        text.append("participants: ").append(session.size()).append('\n');
        text.append("leaderboard: ").append(session.isLeaderboardEnabled()).append('\n');
        text.append("source-of-truth: git\n");
        if (submissions != null) {
            for (int index = 0; index < submissions.size(); index++) {
                Submission submission = submissions.get(index);
                text.append("\n--- checkpoint ").append(submission.id()).append(" ---\n");
                text.append("snapshot: ").append(submission.snapshotTag()).append('\n');
                text.append("workspace: ").append(submission.workspace()).append('\n');
                text.append("note: ").append(oneLine(submission.note())).append('\n');
                text.append("feedback: ").append(oneLine(submission.feedback())).append('\n');
                text.append(submission.content()).append('\n');
            }
        }
        return text.toString();
    }

    /**
     * Writes the patch file below an output directory.
     *
     * @param session the session
     * @param submissions the checkpoint submissions
     * @param outputDir the destination directory
     * @return the written file
     */
    public Path export(ClubSession session, List<Submission> submissions, Path outputDir) {
        Objects.requireNonNull(outputDir, "outputDir");
        Path file = outputDir.resolve("club-session-" + session.id() + ".patch");
        try {
            Files.createDirectories(outputDir);
            Files.write(file, patch(session, submissions).getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot export club session", failure);
        }
        return file;
    }

    /**
     * Returns a redacted roster line for aggregate reporting only.
     *
     * @param session the session
     * @return a count, never a per-person ranking
     */
    public String aggregateRoster(ClubSession session) {
        return "participants=" + session.size() + ",capacity=" + session.capacity();
    }

    private static String oneLine(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
