/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agenttools.api;

import dev.jahaziel.identity.domain.UserId;
import java.nio.file.Path;
import java.util.List;

/** Per-user managed coding-agent tools. Credentials and writable profiles stay user-scoped. */
public interface AgentToolService {
  /** Returns the server-owned catalog annotated with this user's install state. */
  List<ToolStatus> catalog(UserId userId);

  /** Starts or returns the current OpenCode installation operation for this user. */
  ToolStatus installOpenCode(UserId userId);

  /** Removes the managed OpenCode executable while preserving the user's configuration. */
  ToolStatus removeOpenCode(UserId userId);

  /** Returns the managed executable directory added to this user's terminal PATH. */
  Path executableDirectory(UserId userId);

  /** Returns the user-scoped configuration, data, and cache root for CLI sessions. */
  Path profileDirectory(UserId userId);

  /** One catalog entry and the current user's install status. */
  record ToolStatus(
      String id,
      String name,
      String version,
      String architecture,
      String status,
      long downloadSizeBytes,
      long installedSizeBytes,
      int progressPercent,
      String error) {}
}
