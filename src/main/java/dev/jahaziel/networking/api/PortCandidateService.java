/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.api;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.List;

/** Finds loopback listeners owned by live Jahaziel-managed task and terminal process trees. */
public interface PortCandidateService {
  /**
   * Returns likely preview ports for a workspace without registering or publishing them.
   *
   * @param workspaceId the authorized workspace
   * @return current listening ports held by tracked task or terminal processes
   */
  List<Candidate> candidates(WorkspaceId workspaceId);

  /** A listener found in the selected workspace's tracked processes. */
  record Candidate(int port, List<Source> sources) {
    public Candidate {
      if (port < 1 || port > 65535) throw new IllegalArgumentException("port out of range");
      sources = List.copyOf(sources);
      if (sources.isEmpty()) throw new IllegalArgumentException("sources must not be empty");
    }
  }

  /** Process categories that may own a suggested listener. */
  enum Source {
    TASK,
    TERMINAL
  }
}
