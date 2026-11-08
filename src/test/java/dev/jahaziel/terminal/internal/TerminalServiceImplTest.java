/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.terminal.api.TerminalNotFoundException;
import dev.jahaziel.terminal.domain.TerminalSession;
import dev.jahaziel.terminal.domain.TerminalSessionId;
import dev.jahaziel.workspace.api.WorkspaceDirectoryService;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.IllegalWorkspaceStateException;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link TerminalServiceImpl} with no collaborators. */
class TerminalServiceImplTest {
  private TerminalServiceImpl service;
  private WorkspaceId workspaceId;
  private UserId userId;

  @BeforeEach
  void setUp() {
    service = new TerminalServiceImpl();
    workspaceId = new WorkspaceId(UUID.randomUUID());
    userId = new UserId(UUID.randomUUID());
  }

  @Test
  void openGetCloseRoundTrip() {
    TerminalSession session = service.open(workspaceId, userId, 30, 120);
    assertEquals(session.id(), service.get(session.id()).id());
    service.activity(session.id());
    service.close(session.id());
    assertThrows(TerminalNotFoundException.class, () -> service.get(session.id()));
  }

  @Test
  void unknownSessionsThrowWithId() {
    TerminalSessionId id = new TerminalSessionId(UUID.randomUUID());
    TerminalNotFoundException failure =
        assertThrows(TerminalNotFoundException.class, () -> service.get(id));
    assertEquals(id, failure.sessionId());
    assertThrows(TerminalNotFoundException.class, () -> service.close(id));
    assertThrows(TerminalNotFoundException.class, () -> service.activity(id));
  }

  @Test
  void listsOnlyMatchingWorkspace() {
    TerminalSession first = service.open(workspaceId, userId, 30, 120);
    WorkspaceId other = new WorkspaceId(UUID.randomUUID());
    service.open(other, userId, 30, 120);
    var found = service.listByWorkspace(workspaceId);
    assertEquals(1, found.size());
    assertEquals(first.id(), found.get(0).id());
    assertTrue(service.listByWorkspace(new WorkspaceId(UUID.randomUUID())).isEmpty());
  }

  @Test
  void terminalEnvironmentContainsOnlyServerOwnedValues() {
    Path home = Path.of("/data/workspaces/example/home");

    assertEquals(
        Map.of(
            "HOME", home.toString(),
            "PATH", "/usr/local/bin:/usr/bin:/bin",
            "TERM", "xterm-256color",
            "LANG", "C.UTF-8"),
        TerminalServiceImpl.terminalEnvironment(home));
  }

  @Test
  void refusesTerminalsForAWorkspaceThatHasNotStarted() {
    WorkspaceService workspaces = mock(WorkspaceService.class);
    WorkspaceDirectoryService directories = mock(WorkspaceDirectoryService.class);
    WorkspaceProcessRegistry registry = mock(WorkspaceProcessRegistry.class);
    Workspace workspace = mock(Workspace.class);
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    when(workspace.state()).thenReturn(new WorkspaceState.Requested(java.time.Instant.now()));
    TerminalServiceImpl guarded = new TerminalServiceImpl(directories, registry, workspaces);

    IllegalWorkspaceStateException failure =
        assertThrows(
            IllegalWorkspaceStateException.class, () -> guarded.open(workspaceId, userId, 30, 120));

    assertEquals(workspaceId, failure.workspaceId());
    assertEquals("open terminal", failure.action());
  }

  @Test
  void stoppingWorkspaceClosesTheDoorToNewTerminals() {
    WorkspaceService workspaces = mock(WorkspaceService.class);
    WorkspaceDirectoryService directories = mock(WorkspaceDirectoryService.class);
    WorkspaceProcessRegistry registry = mock(WorkspaceProcessRegistry.class);
    Workspace workspace = mock(Workspace.class);
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    when(workspace.state()).thenReturn(new WorkspaceState.Active(java.time.Instant.now()));
    TerminalServiceImpl guarded = new TerminalServiceImpl(directories, registry, workspaces);

    guarded.closeByWorkspace(workspaceId);

    assertThrows(
        IllegalWorkspaceStateException.class, () -> guarded.open(workspaceId, userId, 30, 120));
  }
}
