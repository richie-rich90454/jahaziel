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

/** Terminal session registry. PTY allocation itself happens inside the workspace VM. */
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
}
