/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.domain;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/** One terminal session of a user in a workspace. Survives browser disconnects. */
public final class TerminalSession {
  private final TerminalSessionId id;
  private final WorkspaceId workspaceId;
  private final UserId userId;
  private final Instant startedAt;
  private Instant lastActivityAt;
  private int rowCount;
  private int colCount;
  private volatile Integer exitCode;

  /**
   * Creates a session.
   *
   * @param id the session identifier
   * @param workspaceId the workspace
   * @param userId the owning user
   * @param rowCount terminal rows
   * @param colCount terminal columns
   */
  public TerminalSession(
      TerminalSessionId id, WorkspaceId workspaceId, UserId userId, int rowCount, int colCount) {
    this.id = Objects.requireNonNull(id, "id");
    this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
    this.userId = Objects.requireNonNull(userId, "userId");
    checkSize(rowCount, colCount);
    this.rowCount = rowCount;
    this.colCount = colCount;
    this.startedAt = Instant.now();
    this.lastActivityAt = startedAt;
  }

  /** Returns the session identifier. */
  public TerminalSessionId id() {
    return id;
  }

  /** Returns the workspace. */
  public WorkspaceId workspaceId() {
    return workspaceId;
  }

  /** Returns the owning user. */
  public UserId userId() {
    return userId;
  }

  /** Returns when the session started. */
  public Instant startedAt() {
    return startedAt;
  }

  /** Returns the last activity. */
  public Instant lastActivityAt() {
    return lastActivityAt;
  }

  /** Returns the terminal rows. */
  public int rowCount() {
    return rowCount;
  }

  /** Returns the terminal columns. */
  public int colCount() {
    return colCount;
  }

  /** Returns the process exit status, or {@code null} while the session is running. */
  public Integer exitCode() {
    return exitCode;
  }

  /** Records the process exit status for reconnecting clients. */
  public void markExited(int status) {
    exitCode = status;
    recordActivity();
  }

  /** Marks user activity now. */
  public void recordActivity() {
    lastActivityAt = Instant.now();
  }

  /**
   * Resizes the terminal.
   *
   * @param rows the new row count
   * @param cols the new column count
   */
  public void resize(int rows, int cols) {
    checkSize(rows, cols);
    this.rowCount = rows;
    this.colCount = cols;
    recordActivity();
  }

  private static void checkSize(int rows, int cols) {
    if (rows < 1 || cols < 1 || rows > 200 || cols > 400) {
      throw new IllegalArgumentException(
          "terminal dimensions must be within 1-200 rows and 1-400 columns");
    }
  }
}
