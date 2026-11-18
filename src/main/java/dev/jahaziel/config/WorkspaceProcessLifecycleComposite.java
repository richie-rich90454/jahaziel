/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import dev.jahaziel.debug.internal.DebugServiceImpl;
import dev.jahaziel.language.internal.LanguageServerProcessManager;
import dev.jahaziel.workspace.api.WorkspaceProcessLifecycle;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;

/** Delegates workspace process shutdown to each interactive process manager. */
final class WorkspaceProcessLifecycleComposite implements WorkspaceProcessLifecycle {
  private final LanguageServerProcessManager languageProcesses;
  private final ObjectProvider<DebugServiceImpl> debugProcesses;

  WorkspaceProcessLifecycleComposite(
      LanguageServerProcessManager languageProcesses,
      ObjectProvider<DebugServiceImpl> debugProcesses) {
    this.languageProcesses = Objects.requireNonNull(languageProcesses, "languageProcesses");
    this.debugProcesses = Objects.requireNonNull(debugProcesses, "debugProcesses");
  }

  @Override
  public void closeByWorkspace(WorkspaceId workspaceId) {
    languageProcesses.closeByWorkspace(workspaceId);
    debugProcesses.getObject().closeByWorkspace(workspaceId);
  }

  @Override
  public void allowByWorkspace(WorkspaceId workspaceId) {
    languageProcesses.allowByWorkspace(workspaceId);
    debugProcesses.getObject().allowByWorkspace(workspaceId);
  }

  @Override
  public void deleteWorkspace(WorkspaceId workspaceId) {
    languageProcesses.deleteWorkspace(workspaceId);
    debugProcesses.getObject().deleteWorkspace(workspaceId);
  }
}
