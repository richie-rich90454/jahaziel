/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

final class CollaborationCommentTest {
    @Test
    void pathContainmentRejectsTraversalAndAbsolutePaths() {
        final String traversal = "../etc/passwd";
        final String absolute = "/etc/passwd";
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        WorkspacePaths.validate(traversal);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        WorkspacePaths.validate(absolute);
                    }
                });
        assertEquals("src/Main.java", WorkspacePaths.validate("src/Main.java"));
        assertTrue(WorkspacePaths.contained("src/Main.java", "src"));
        assertFalse(WorkspacePaths.contained("other/Main.java", "src"));
    }

    @Test
    void createListResolveReplyAndDelete() {
        CollaborationFixture fixture = new CollaborationFixture(1000L);
        FileComment comment =
                fixture.comments.create(
                        CollaborationFixture.WORKSPACE,
                        CollaborationFixture.EDITOR,
                        "src/Main.java",
                        2,
                        4,
                        "review this",
                        1000L);
        List<FileComment> listed =
                fixture.comments.list(CollaborationFixture.WORKSPACE, "src/Main.java");
        assertEquals(1, listed.size());
        assertEquals(comment.id(), listed.get(0).id());

        fixture.comments.reply(
                CollaborationFixture.WORKSPACE,
                CollaborationFixture.OWNER,
                comment.id(),
                "agreed",
                2000L);
        assertEquals(
                1, fixture.comments.replies(CollaborationFixture.WORKSPACE, comment.id()).size());

        fixture.comments.resolve(
                CollaborationFixture.WORKSPACE,
                CollaborationFixture.EDITOR,
                comment.id(),
                true,
                3000L);
        assertTrue(fixture.store.comment(comment.id()).resolved());

        fixture.comments.delete(
                CollaborationFixture.WORKSPACE, CollaborationFixture.OWNER, comment.id());
        assertEquals(null, fixture.store.comment(comment.id()));
    }

    @Test
    void viewerCannotCreateAndNonAuthorCannotResolve() {
        final CollaborationFixture fixture = new CollaborationFixture(1000L);
        assertThrows(
                CollaborationAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.comments.create(
                                CollaborationFixture.WORKSPACE,
                                CollaborationFixture.VIEWER,
                                "src/Main.java",
                                1,
                                1,
                                "nope",
                                1000L);
                    }
                });
        final FileComment comment =
                fixture.comments.create(
                        CollaborationFixture.WORKSPACE,
                        CollaborationFixture.EDITOR,
                        "src/Main.java",
                        1,
                        1,
                        "ok",
                        1000L);
        assertThrows(
                CollaborationAccessDeniedException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.comments.resolve(
                                CollaborationFixture.WORKSPACE,
                                CollaborationFixture.VIEWER,
                                comment.id(),
                                true,
                                2000L);
                    }
                });
    }

    @Test
    void invalidLineRangeIsRejected() {
        final CollaborationFixture fixture = new CollaborationFixture(1000L);
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.comments.create(
                                CollaborationFixture.WORKSPACE,
                                CollaborationFixture.EDITOR,
                                "src/Main.java",
                                5,
                                2,
                                "bad range",
                                1000L);
                    }
                });
    }
}
