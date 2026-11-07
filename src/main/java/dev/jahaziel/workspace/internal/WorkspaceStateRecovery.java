/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Marks interrupted workspace lifecycles recoverable after a server restart. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public final class WorkspaceStateRecovery implements ApplicationRunner {
  private static final String RESTART_REASON =
      "server restarted while workspace was starting or running; files are preserved, start to recover";

  private final WorkspaceService workspaces;

  public WorkspaceStateRecovery(WorkspaceService workspaces) {
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
  }

  @Override
  public void run(ApplicationArguments arguments) {
    for (var view : workspaces.describeAll()) {
      Workspace workspace = view.workspace();
      WorkspaceState state = workspace.state();
      if (state instanceof WorkspaceState.Preparing
          || state instanceof WorkspaceState.Booting
          || state instanceof WorkspaceState.Ready
          || state instanceof WorkspaceState.Active
          || state instanceof WorkspaceState.Idle) {
        workspace.fail(RESTART_REASON);
        workspaces.save(workspace);
      }
    }
  }
}
