/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.agent.AgentAudit;
import dev.jahaziel.agent.AgentException;
import dev.jahaziel.agent.AgentStore;
import dev.jahaziel.agent.support.FakeAuditSink;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.spi.AuditEvent;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class DiffReviewServiceTest {
    @TempDir Path temporary;

    private final UserId user = new UserId(UUID.randomUUID());
    private final WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());

    @Test
    void hunksAreReviewedIndependentlyAndEveryDecisionIsAudited() {
        FakeAuditSink audit = new FakeAuditSink();
        DiffReviewService service = service(audit);
        UnifiedPatch patch = service.stage(user, workspace, validPatch());

        assertEquals(2, patch.getHunks().size());
        assertEquals(UnifiedPatch.PENDING, patch.getDecisions().get(0));

        service.accept(user, workspace, patch.getId(), 0);
        service.reject(user, workspace, patch.getId(), 1);

        UnifiedPatch stored = service.get(user, workspace, patch.getId());
        assertEquals(UnifiedPatch.ACCEPTED, stored.getDecisions().get(0));
        assertEquals(UnifiedPatch.REJECTED, stored.getDecisions().get(1));

        List<AuditEvent> events = audit.events();
        assertEquals(2, events.size());
        assertEquals("PATCH_HUNK_ACCEPTED", events.get(0).action());
        assertEquals("PATCH_HUNK_REJECTED", events.get(1).action());
    }

    @Test
    void acceptingAHunkNeverAppliesItToTheWorkspace() throws Exception {
        DiffReviewService service = service(new FakeAuditSink());
        AgentStore store = store();
        Path file = store.resolveWorkspacePath(workspace.value(), "src/App.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "old\n", StandardCharsets.UTF_8);

        UnifiedPatch patch = service.stage(user, workspace, validPatch());
        service.accept(user, workspace, patch.getId(), 0);

        assertEquals("old\n", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void parserRejectsPathsThatEscapeTheWorkspace() {
        final DiffReviewService service = service(new FakeAuditSink());
        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        service.stage(user, workspace, patchFor("../../secrets.txt"));
                    }
                });
        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        service.stage(user, workspace, patchFor("/etc/passwd"));
                    }
                });
    }

    @Test
    void parserRejectsInconsistentHunkCountsAndUnknownLines() {
        final DiffReviewService service = service(new FakeAuditSink());
        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        service.stage(
                                user,
                                workspace,
                                "--- a/src/App.java\n+++ b/src/App.java\n@@ -1,5 +1,5 @@\n-old\n+new\n");
                    }
                });
        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        service.stage(
                                user,
                                workspace,
                                "--- a/src/App.java\n+++ b/src/App.java\n@@ -1 +1 @@\nSURPRISE\n");
                    }
                });
    }

    @Test
    void stagedPatchesStayUnderTheDataDirectory() {
        Path data = temporary.resolve("data");
        DiffReviewService service = service(new FakeAuditSink());
        UnifiedPatch patch = service.stage(user, workspace, validPatch());

        Path stored =
                data.resolve("agent")
                        .resolve("patches")
                        .resolve(user.value().toString())
                        .resolve(workspace.value().toString())
                        .resolve(patch.getId() + ".json");
        assertTrue(Files.isRegularFile(stored));
        assertTrue(stored.toAbsolutePath().startsWith(data.toAbsolutePath()));
        assertEquals(1, service.list(user, workspace).size());
        assertFalse(service.list(user, workspace).isEmpty());
    }

    private AgentStore store() {
        return new AgentStore(temporary.resolve("data"), new ObjectMapper());
    }

    private DiffReviewService service(FakeAuditSink sink) {
        AgentStore store = store();
        return new DiffReviewService(store, new StrictPatchParser(store), new AgentAudit(sink));
    }

    private static String validPatch() {
        return "--- a/src/App.java\n"
                + "+++ b/src/App.java\n"
                + "@@ -1,2 +1,2 @@\n"
                + " package app;\n"
                + "-old\n"
                + "+new\n"
                + "@@ -10,2 +10,2 @@\n"
                + "-a\n"
                + "+b\n"
                + " c\n";
    }

    private static String patchFor(String path) {
        return "--- a/" + path + "\n" + "+++ b/" + path + "\n" + "@@ -1 +1 @@\n" + "-x\n" + "+y\n";
    }

    private static void assertThrowsAgent(Executable executable) {
        try {
            executable.execute();
            fail("expected an AgentException");
        } catch (Throwable failure) {
            assertTrue(failure instanceof AgentException);
        }
    }
}
