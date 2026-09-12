/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.internal;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.terminal.api.TerminalNotFoundException;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.terminal.domain.TerminalSession;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory terminal sessions. Sessions die with the control plane; PTYs live in the VM. */
public final class TerminalServiceImpl implements TerminalService {
  private final ConcurrentMap<TerminalSessionId, TerminalSession> sessions =
      new ConcurrentHashMap<>();

  @Override
  public TerminalSession open(WorkspaceId workspaceId, UserId userId, int rows, int cols) {
    TerminalSession session =
        new TerminalSession(
            new TerminalSessionId(UUID.randomUUID()), workspaceId, userId, rows, cols);
    sessions.put(session.id(), session);
    return session;
  }

  @Override
  public TerminalSession get(TerminalSessionId id) {
    TerminalSession session = sessions.get(Objects.requireNonNull(id, "id"));
    if (session == null) {
      throw new TerminalNotFoundException(id);
    }
    return session;
  }

  @Override
  public void close(TerminalSessionId id) {
    if (sessions.remove(Objects.requireNonNull(id, "id")) == null) {
      throw new TerminalNotFoundException(id);
    }
  }

  @Override
  public List<TerminalSession> listByWorkspace(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    return sessions.values().stream()
        .filter(session -> session.workspaceId().equals(workspaceId))
        .toList();
  }

  @Override
  public void activity(TerminalSessionId id) {
    get(id).recordActivity();
  }
}
