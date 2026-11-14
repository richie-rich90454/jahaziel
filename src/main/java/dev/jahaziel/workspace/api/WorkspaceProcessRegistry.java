/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.api;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Tracks workspace processes so interrupted server restarts can stop stale processes safely. */
public interface WorkspaceProcessRegistry {
  /** The server-managed process categories that may be restarted with the application. */
  enum Kind {
    TERMINAL,
    TASK,
    SETUP,
    LANGUAGE_SERVER,
    DEBUG_ADAPTER
  }

  /** Records a process and returns a handle that removes its marker when it exits. */
  Registration track(WorkspaceId workspaceId, Kind kind, Process process);

  /**
   * Wraps a server-owned command in its own Linux session so child processes can be recovered as a
   * group after an abrupt server restart.
   */
  List<String> dedicatedSessionCommand(List<String> command);

  /**
   * Lists live members of the server-managed process groups for one workspace. Only processes
   * whose root still matches its persisted identity are included.
   *
   * @param workspaceId the workspace whose processes may be inspected
   * @param kinds the managed process categories to include
   * @return process ids limited to the selected workspace and categories
   */
  default List<TrackedProcess> trackedProcessTree(WorkspaceId workspaceId, Set<Kind> kinds) {
    return List.of();
  }

  /** A process marker registration. Closing it is idempotent. */
  interface Registration extends AutoCloseable {
    UUID id();

    @Override
    void close();
  }

  /** A live process id and the kind of server-managed root that owns it. */
  record TrackedProcess(Kind kind, long processId) {
    public TrackedProcess {
      if (kind == null) throw new NullPointerException("kind");
      if (processId < 1) throw new IllegalArgumentException("processId must be positive");
    }
  }
}
