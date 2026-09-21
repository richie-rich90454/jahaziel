/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
            new SecurityViolationEvent(workspace, "cgroup-escape", Map.of("pid", "9"), at),
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
    assertThrows(NullPointerException.class, () -> new WorkspaceDeletedEvent(null, at));
    assertThrows(IllegalArgumentException.class, () -> new PolicyChangedEvent("  ", "", at));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SecurityViolationEvent(workspace, "  ", Map.of(), at));
    assertThrows(IllegalArgumentException.class, () -> new NodeLostEvent("n", "  ", at));
  }

  @Test
  void violationDetailsAreCopied() {
    Map<String, String> details = new HashMap<>(Map.of("k", "v"));
    SecurityViolationEvent event = new SecurityViolationEvent(workspace, "t", details, at);
    details.put("evil", "x");
    assertEquals(Map.of("k", "v"), event.details());
  }

  private static String describe(DomainEvent event) {
    return switch (event) {
      case WorkspaceCreatedEvent e -> "created " + e.workspaceId();
      case WorkspaceDeletedEvent e -> "deleted " + e.workspaceId();
      case RuntimeStartedEvent e -> "started " + e.workspaceId();
      case RuntimeStoppedEvent e -> "stopped " + e.workspaceId();
      case SnapshotCreatedEvent e -> "snapshot " + e.snapshotId();
      case PolicyChangedEvent e -> "policy " + e.scope();
      case TerminalSessionStartedEvent e -> "terminal " + e.sessionId();
      case SecurityViolationEvent e -> "violation " + e.violationType();
      case NodeRegisteredEvent e -> "registered " + e.nodeId();
      case NodeLostEvent e -> "lost " + e.nodeId();
    };
  }
}
