/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.club.api.ClubAccessDeniedException;
import dev.jahaziel.club.domain.ClubRoles;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.session.ClubSessionService;
import dev.jahaziel.club.showcase.ShowcaseGallery;
import dev.jahaziel.club.showcase.ShowcaseGallery.Project;
import dev.jahaziel.club.support.Fakes;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Checkpoint submission, instructor review, and admin-only showcase promotion. */
class CheckpointServiceTest {
    private Fakes.Snapshots snapshots;
    private Fakes.Authorization authorization;
    private ShowcaseGallery gallery;
    private CheckpointService checkpoints;
    private ClubSession session;
    private UserId host;
    private UserId attendee;
    private WorkspaceId workspace;

    @BeforeEach
    void setUp() {
        Fakes.Workspace workspaces = new Fakes.Workspace();
        Fakes.Tasks tasks = new Fakes.Tasks();
        snapshots = new Fakes.Snapshots();
        authorization = new Fakes.Authorization();
        gallery = new ShowcaseGallery(authorization);
        checkpoints = new CheckpointService(snapshots, gallery, authorization);
        ClubSessionService sessions =
                new ClubSessionService(workspaces, tasks, new NodeId("node-a"));
        host = new UserId(UUID.randomUUID());
        attendee = new UserId(UUID.randomUUID());
        session =
                sessions.createSession(
                        host,
                        "java",
                        Instant.parse("2026-11-19T10:00:00Z"),
                        Instant.parse("2026-11-19T12:00:00Z"),
                        10);
        session.join(attendee, ClubRoles.ATTENDEE);
        workspace = sessions.forkFor(session, attendee).id();
    }

    @Test
    void attendeeSubmitsSnapshotAndStaffReviewsDiff() {
        CheckpointService.Submission submission =
                checkpoints.submit(
                        session, attendee, workspace, "checkpoint-1", "first try", "a\nx\nc\n");
        assertEquals("checkpoint-1", snapshots.tags.get(0));
        assertEquals(attendee, submission.user());

        String diff = checkpoints.diff(session, host, submission.id(), "a\nb\nc\n");
        assertTrue(diff.contains("-b"));
        assertTrue(diff.contains("+x"));

        assertThrows(
                ClubAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        checkpoints.diff(session, attendee, submission.id(), "a\nb\nc\n");
                    }
                });
    }

    @Test
    void feedbackIsStaffOnly() {
        final CheckpointService.Submission submission =
                checkpoints.submit(session, attendee, workspace, "checkpoint-1", "note", "content");
        assertThrows(
                ClubAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        checkpoints.feedback(session, attendee, submission.id(), "nice");
                    }
                });
        checkpoints.feedback(session, host, submission.id(), "almost there");
        assertEquals("almost there", submission.feedback());
    }

    @Test
    void promotionRequiresAdminAndPublishesReadOnlyPreview() {
        final CheckpointService.Submission submission =
                checkpoints.submit(
                        session, attendee, workspace, "checkpoint-1", "final", "content");
        List<String> tags = new ArrayList<String>();
        tags.add("java");
        List<String> screenshots = new ArrayList<String>();
        screenshots.add("preview.png");

        assertThrows(
                ClubAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        checkpoints.promote(
                                session, host, submission.id(), "Demo", tags, screenshots);
                    }
                });

        authorization.addAdmin(host);
        Project project =
                checkpoints.promote(session, host, submission.id(), "Demo", tags, screenshots);
        assertEquals(1, gallery.size());
        assertNotNull(gallery.find(project.id()));
        assertFalse(submission.id().isEmpty());
        assertEquals(project.id(), submission.promotedTo());
        assertTrue(project.tags().contains("java"));
        assertThrows(
                UnsupportedOperationException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        project.tags().add("mutated");
                    }
                });
        assertFalse(project.screenshots().isEmpty());
    }
}
