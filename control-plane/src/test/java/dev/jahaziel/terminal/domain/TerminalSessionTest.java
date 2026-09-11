/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link TerminalSession}. */
class TerminalSessionTest {
  private TerminalSessionId sessionId;
  private WorkspaceId workspaceId;
  private UserId userId;

  @BeforeEach
  void setUp() {
    sessionId = new TerminalSessionId(UUID.randomUUID());
    workspaceId = new WorkspaceId(UUID.randomUUID());
    userId = new UserId(UUID.randomUUID());
  }

  @Test
  void startsWithGivenSize() {
    TerminalSession session = new TerminalSession(sessionId, workspaceId, userId, 30, 120);
    assertEquals(sessionId, session.id());
    assertEquals(workspaceId, session.workspaceId());
    assertEquals(userId, session.userId());
    assertEquals(30, session.rowCount());
    assertEquals(120, session.colCount());
    assertEquals(session.startedAt(), session.lastActivityAt());
  }

  @Test
  void resizesAndRecordsActivity() {
    TerminalSession session = new TerminalSession(sessionId, workspaceId, userId, 30, 120);
    session.resize(40, 160);
    assertEquals(40, session.rowCount());
    assertEquals(160, session.colCount());
    assertTrue(!session.lastActivityAt().isBefore(session.startedAt()));
  }

  @Test
  void nonPositiveSizesAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TerminalSession(sessionId, workspaceId, userId, 0, 120));
    TerminalSession session = new TerminalSession(sessionId, workspaceId, userId, 30, 120);
    assertThrows(IllegalArgumentException.class, () -> session.resize(30, 0));
  }

  @Test
  void nullsAreRejected() {
    assertThrows(
        NullPointerException.class, () -> new TerminalSession(null, workspaceId, userId, 30, 120));
  }
}
