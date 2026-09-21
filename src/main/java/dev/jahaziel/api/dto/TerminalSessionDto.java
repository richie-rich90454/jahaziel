/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.terminal.domain.TerminalSession;
import java.time.Instant;
import java.util.UUID;

/**
 * A terminal session for reads.
 *
 * @param sessionId the session identifier
 * @param workspaceId the workspace identifier
 * @param userId the owning user identifier
 * @param startedAt when the session started
 * @param lastActivityAt the last activity
 * @param rows terminal rows
 * @param cols terminal columns
 */
public record TerminalSessionDto(
    UUID sessionId,
    UUID workspaceId,
    UUID userId,
    Instant startedAt,
    Instant lastActivityAt,
    int rows,
    int cols) {
  /**
   * Maps a session to a DTO.
   *
   * @param session the session
   * @return the DTO
   */
  public static TerminalSessionDto from(TerminalSession session) {
    return new TerminalSessionDto(
        session.id().value(),
        session.workspaceId().value(),
        session.userId().value(),
        session.startedAt(),
        session.lastActivityAt(),
        session.rowCount(),
        session.colCount());
  }
}
