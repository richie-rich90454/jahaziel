/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Behavior of {@link ApprovalQueue}: gating, decisions and persistence. */
class ApprovalQueueTest {
    @TempDir Path temp;

    private Map<String, String> payload(String value) {
        Map<String, String> values = new HashMap<String, String>();
        values.put("artifact", value);
        return values;
    }

    @Test
    void changesAreGatedUntilApproved() {
        ApprovalQueue queue = new ApprovalQueue(temp.resolve("data"));
        String id = queue.submit("catalog.addition", "alice", "python@3.12", payload("sha256"));
        assertFalse(queue.isApproved("catalog.addition", "python@3.12"));
        assertEquals(1, queue.pending().size());
        queue.approve(id, "bob");
        assertTrue(queue.isApproved("catalog.addition", "python@3.12"));
        assertEquals(ApprovalQueue.APPROVED, queue.find(id).status());
        assertEquals(0, queue.pending().size());
        String text = OpsFiles.readText(queue.decisionsFile());
        assertTrue(text.contains("bob"));
        assertTrue(text.contains("APPROVED"));
    }

    @Test
    void rejectionKeepsGateClosed() {
        ApprovalQueue queue = new ApprovalQueue(temp.resolve("data"));
        String id = queue.submit("role.change", "alice", "admin", payload("reason"));
        queue.reject(id, "bob", "not warranted");
        assertFalse(queue.isApproved("role.change", "admin"));
        assertEquals(ApprovalQueue.REJECTED, queue.find(id).status());
        assertEquals(1, queue.decisions().size());
    }

    @Test
    void approvalIsFinal() {
        final ApprovalQueue queue = new ApprovalQueue(temp.resolve("data"));
        final String id = queue.submit("share.scope", "alice", "ws1", payload("public"));
        queue.approve(id, "bob");
        assertThrows(
                IllegalStateException.class,
                new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        queue.approve(id, "carol");
                    }
                });
    }

    @Test
    void queueSurvivesRestart() {
        Path data = temp.resolve("data");
        ApprovalQueue first = new ApprovalQueue(data);
        String id = first.submit("template.add", "alice", "java", payload("template"));
        first.approve(id, "bob");
        ApprovalQueue second = new ApprovalQueue(data);
        assertEquals(1, second.requests().size());
        assertTrue(second.isApproved("template.add", "java"));
    }
}
