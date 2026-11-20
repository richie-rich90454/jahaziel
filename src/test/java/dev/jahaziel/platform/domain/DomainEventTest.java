/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every domain event carries its instant and dispatches exhaustively. */
class DomainEventTest {

    private final UUID workspace = UUID.randomUUID();

    private final Instant at = Instant.now();

    @Test
    void allVariantsAreDomainEvents() {
        List<DomainEvent> events =
                List.of(
                        new WorkspaceCreatedEvent(workspace, UUID.randomUUID(), at),
                        new WorkspaceDeletedEvent(workspace, at),
                        new RuntimeStartedEvent(workspace, at),
                        new RuntimeStoppedEvent(workspace, at),
                        new SnapshotCreatedEvent(workspace, UUID.randomUUID(), at),
                        new PolicyChangedEvent("global", "", at),
                        new TerminalSessionStartedEvent(UUID.randomUUID(), workspace, at),
                        new SecurityViolationEvent(
                                workspace, "cgroup-escape", Map.of("pid", "9"), at),
                        new NodeRegisteredEvent("node-a", at),
                        new NodeLostEvent("node-a", "no heartbeat", at));
        assertEquals(10, events.size());
        for (DomainEvent event : events) {
            assertEquals(at, event.occurredAt());
            assertFalse(describe(event).isBlank());
        }
    }

    @Test
    void nullsAreRejected() {
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        new WorkspaceDeletedEvent(null, at);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        new PolicyChangedEvent("  ", "", at);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        new SecurityViolationEvent(workspace, "  ", Map.of(), at);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {

                    public void execute() {
                        new NodeLostEvent("n", "  ", at);
                    }
                });
    }

    @Test
    void violationDetailsAreCopied() {
        Map<String, String> details = new HashMap<>(Map.of("k", "v"));
        SecurityViolationEvent event = new SecurityViolationEvent(workspace, "t", details, at);
        details.put("evil", "x");
        assertEquals(Map.of("k", "v"), event.details());
    }

    private static String describe(DomainEvent event) {
        if (event instanceof WorkspaceCreatedEvent) {
            WorkspaceCreatedEvent e = (WorkspaceCreatedEvent) event;
            return "created " + e.workspaceId();
        }
        if (event instanceof WorkspaceDeletedEvent) {
            WorkspaceDeletedEvent e = (WorkspaceDeletedEvent) event;
            return "deleted " + e.workspaceId();
        }
        if (event instanceof RuntimeStartedEvent) {
            RuntimeStartedEvent e = (RuntimeStartedEvent) event;
            return "started " + e.workspaceId();
        }
        if (event instanceof RuntimeStoppedEvent) {
            RuntimeStoppedEvent e = (RuntimeStoppedEvent) event;
            return "stopped " + e.workspaceId();
        }
        if (event instanceof SnapshotCreatedEvent) {
            SnapshotCreatedEvent e = (SnapshotCreatedEvent) event;
            return "snapshot " + e.snapshotId();
        }
        if (event instanceof PolicyChangedEvent) {
            PolicyChangedEvent e = (PolicyChangedEvent) event;
            return "policy " + e.scope();
        }
        if (event instanceof TerminalSessionStartedEvent) {
            TerminalSessionStartedEvent e = (TerminalSessionStartedEvent) event;
            return "terminal " + e.sessionId();
        }
        if (event instanceof SecurityViolationEvent) {
            SecurityViolationEvent e = (SecurityViolationEvent) event;
            return "violation " + e.violationType();
        }
        if (event instanceof NodeRegisteredEvent) {
            NodeRegisteredEvent e = (NodeRegisteredEvent) event;
            return "registered " + e.nodeId();
        }
        NodeLostEvent e = (NodeLostEvent) event;
        return "lost " + e.nodeId();
    }
}
