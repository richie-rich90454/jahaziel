/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.api;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.terminal.domain.TerminalSession;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.List;
import java.util.function.Consumer;

/**
 * Terminal session registry. PTYs run in the trusted host account, rooted in the workspace home.
 */
public interface TerminalService {
  /**
   * Opens a session.
   *
   * @param workspaceId the workspace
   * @param userId the owning user
   * @param rows terminal rows
   * @param cols terminal columns
   * @return the session
   */
  TerminalSession open(WorkspaceId workspaceId, UserId userId, int rows, int cols);

  /**
   * Returns one session.
   *
   * @param id the session identifier
   * @return the session
   * @throws TerminalNotFoundException when the session does not exist
   */
  TerminalSession get(TerminalSessionId id);

  /**
   * Closes a session.
   *
   * @param id the session identifier
   * @throws TerminalNotFoundException when the session does not exist
   */
  void close(TerminalSessionId id);

  /**
   * Lists a workspace's sessions.
   *
   * @param workspaceId the workspace
   * @return the sessions
   */
  List<TerminalSession> listByWorkspace(WorkspaceId workspaceId);

  /**
   * Records activity on a session.
   *
   * @param id the session identifier
   * @throws TerminalNotFoundException when the session does not exist
   */
  void activity(TerminalSessionId id);

  /** Subscribes to process events and immediately replays buffered output and an exit status. */
  AutoCloseable attach(TerminalSessionId id, Consumer<StreamEvent> output);

  /** Writes terminal input to the process stdin. */
  void write(TerminalSessionId id, String input);

  /** Updates the PTY dimensions. */
  void resize(TerminalSessionId id, int rows, int cols);

  /** Closes every live PTY for a workspace. */
  void closeByWorkspace(WorkspaceId workspaceId);

  /** Allows new PTYs after the workspace has successfully started. */
  default void allowByWorkspace(WorkspaceId workspaceId) {}

  /** Closes every live PTY during server shutdown. */
  void closeAll();

  /** One stream event delivered to the browser. */
  record StreamEvent(String type, String data, Integer exitCode) {}
}
