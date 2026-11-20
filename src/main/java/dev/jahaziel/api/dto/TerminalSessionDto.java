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
public final class TerminalSessionDto {

    private final UUID sessionId;

    private final UUID workspaceId;

    private final UUID userId;

    private final Instant startedAt;

    private final Instant lastActivityAt;

    private final int rows;

    private final int cols;

    public TerminalSessionDto(
            UUID sessionId,
            UUID workspaceId,
            UUID userId,
            Instant startedAt,
            Instant lastActivityAt,
            int rows,
            int cols) {
        this.sessionId = sessionId;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.startedAt = startedAt;
        this.lastActivityAt = lastActivityAt;
        this.rows = rows;
        this.cols = cols;
    }

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

    public UUID sessionId() {
        return this.sessionId;
    }

    public UUID getSessionId() {
        return this.sessionId;
    }

    public UUID workspaceId() {
        return this.workspaceId;
    }

    public UUID getWorkspaceId() {
        return this.workspaceId;
    }

    public UUID userId() {
        return this.userId;
    }

    public UUID getUserId() {
        return this.userId;
    }

    public Instant startedAt() {
        return this.startedAt;
    }

    public Instant getStartedAt() {
        return this.startedAt;
    }

    public Instant lastActivityAt() {
        return this.lastActivityAt;
    }

    public Instant getLastActivityAt() {
        return this.lastActivityAt;
    }

    public int rows() {
        return this.rows;
    }

    public int getRows() {
        return this.rows;
    }

    public int cols() {
        return this.cols;
    }

    public int getCols() {
        return this.cols;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TerminalSessionDto)) return false;
        TerminalSessionDto other = (TerminalSessionDto) o;
        return (java.util.Objects.equals(this.sessionId, other.sessionId))
                && java.util.Objects.equals(this.workspaceId, other.workspaceId)
                && java.util.Objects.equals(this.userId, other.userId)
                && java.util.Objects.equals(this.startedAt, other.startedAt)
                && java.util.Objects.equals(this.lastActivityAt, other.lastActivityAt)
                && this.rows == other.rows
                && this.cols == other.cols;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.sessionId,
                this.workspaceId,
                this.userId,
                this.startedAt,
                this.lastActivityAt,
                this.rows,
                this.cols);
    }

    @Override
    public String toString() {
        return "TerminalSessionDto["
                + "sessionId="
                + this.sessionId
                + ", "
                + "workspaceId="
                + this.workspaceId
                + ", "
                + "userId="
                + this.userId
                + ", "
                + "startedAt="
                + this.startedAt
                + ", "
                + "lastActivityAt="
                + this.lastActivityAt
                + ", "
                + "rows="
                + this.rows
                + ", "
                + "cols="
                + this.cols
                + "]";
    }
}
