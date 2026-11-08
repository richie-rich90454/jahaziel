/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.terminal.api.TerminalService;
import dev.jahaziel.workspace.api.WorkspaceProcessLifecycle;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceTaskService;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Lifecycle behavior over the backend capability seam. */
class RuntimeServiceImplTest {
  private FakeBackend backend;
  private WorkspaceService workspaces;
  private PortService ports;
  private BootProgressService progress;
  private TerminalService terminals;
  private WorkspaceTaskService tasks;
  private WorkspaceProcessLifecycle languageProcesses;
  private RuntimeService service;
  private WorkspaceId workspaceId;

  @BeforeEach
  void setUp() {
    backend = new FakeBackend();
    workspaces = mock(WorkspaceService.class);
    ports = mock(PortService.class);
    progress = mock(BootProgressService.class);
    terminals = mock(TerminalService.class);
    tasks = mock(WorkspaceTaskService.class);
    languageProcesses = mock(WorkspaceProcessLifecycle.class);
    service =
        new RuntimeServiceImpl(
            backend,
            workspaces,
            new ImageRef("base-image:2026.09"),
            ports,
            progress,
            terminals,
            tasks,
            languageProcesses);
    workspaceId = new WorkspaceId(UUID.randomUUID());
  }

  @Test
  void requestedWorkspaceIsPreparedStartedAndMarkedReady() {
    Workspace workspace = workspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    when(tasks.setupStatus(workspaceId))
        .thenReturn(new WorkspaceTaskService.SetupStatus("PENDING", "", "", null, null));

    Workspace started = service.start(workspaceId);

    assertInstanceOf(WorkspaceState.Ready.class, started.state());
    assertEquals(1, backend.prepares);
    assertEquals(1, backend.starts);
    verify(workspaces, times(3)).save(workspace);
    verify(progress).report(workspaceId, BootProgressService.BootPhase.PREPARING);
    verify(progress).report(workspaceId, BootProgressService.BootPhase.STARTING);
    verify(progress).report(workspaceId, BootProgressService.BootPhase.READY);
    verify(tasks).allowByWorkspace(workspaceId);
    verify(tasks).retrySetup(workspaceId);
    verify(terminals).allowByWorkspace(workspaceId);
  }

  @Test
  void pendingSetupSchedulingFailureDoesNotFailStartedWorkspace() {
    Workspace workspace = workspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    when(tasks.setupStatus(workspaceId))
        .thenThrow(new IllegalStateException("setup metadata unavailable"));

    assertInstanceOf(WorkspaceState.Ready.class, service.start(workspaceId).state());

    assertEquals(1, backend.starts);
    verify(workspaces, times(3)).save(workspace);
    verify(tasks, never()).retrySetup(workspaceId);
  }

  @Test
  void interruptedBootIsReconciledThroughBackendCapabilities() {
    Workspace workspace = workspace();
    workspace.prepare();
    workspace.boot();
    when(workspaces.get(workspaceId)).thenReturn(workspace);

    assertInstanceOf(WorkspaceState.Ready.class, service.start(workspaceId).state());
    assertEquals(1, backend.prepares);
    assertEquals(1, backend.starts);
  }

  @Test
  void alreadyReadyWorkspaceDoesNotRestartBackend() {
    Workspace workspace = readyWorkspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);

    assertInstanceOf(WorkspaceState.Ready.class, service.start(workspaceId).state());

    assertEquals(0, backend.prepares);
    assertEquals(0, backend.starts);
  }

  @Test
  void startRecoversSuspendedWorkspaceFromItsPreservedHome() {
    Workspace workspace = activeWorkspace();
    workspace.suspend();
    when(workspaces.get(workspaceId)).thenReturn(workspace);

    assertInstanceOf(WorkspaceState.Ready.class, service.start(workspaceId).state());

    assertEquals(1, backend.prepares);
    assertEquals(1, backend.starts);
    verify(workspaces, times(4)).save(workspace);
  }

  @Test
  void startRetriesWorkspaceMarkedFailedAfterServerRestart() {
    Workspace workspace = activeWorkspace();
    workspace.fail("server restarted during boot");
    when(workspaces.get(workspaceId)).thenReturn(workspace);

    assertInstanceOf(WorkspaceState.Ready.class, service.start(workspaceId).state());

    assertEquals(1, backend.prepares);
    assertEquals(1, backend.starts);
    verify(workspaces, times(4)).save(workspace);
  }

  @Test
  void failedStartPersistsARecoverableFailureReason() {
    Workspace workspace = workspace();
    backend.startFailure = new IllegalStateException("storage unavailable");
    when(workspaces.get(workspaceId)).thenReturn(workspace);

    assertThrows(IllegalStateException.class, () -> service.start(workspaceId));

    WorkspaceState.Failed failed = assertInstanceOf(WorkspaceState.Failed.class, workspace.state());
    assertTrue(failed.reason().startsWith("storage unavailable (operation "));
    assertTrue(failed.reason().endsWith(")"));
    verify(progress).clear(workspaceId);
    verify(workspaces, times(2)).save(workspace);
  }

  @Test
  void stopClosesSessionsAndTasksThenSuspendsRuntime() {
    Workspace workspace = activeWorkspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);

    assertInstanceOf(WorkspaceState.Suspended.class, service.stop(workspaceId).state());

    verify(terminals).closeByWorkspace(workspaceId);
    verify(tasks).closeByWorkspace(workspaceId);
    verify(languageProcesses).closeByWorkspace(workspaceId);
    assertEquals(1, backend.stops);
    verify(workspaces).save(workspace);
  }

  @Test
  void stoppingRequestedWorkspaceDoesNotStartOrStopBackend() {
    Workspace workspace = workspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);

    assertInstanceOf(WorkspaceState.Requested.class, service.stop(workspaceId).state());

    assertEquals(0, backend.starts);
    assertEquals(0, backend.stops);
    verify(terminals).closeByWorkspace(workspaceId);
    verify(tasks).closeByWorkspace(workspaceId);
    verify(languageProcesses).closeByWorkspace(workspaceId);
  }

  @Test
  void restartStopsThenPreparesAndStartsAgain() {
    Workspace workspace = activeWorkspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);

    assertInstanceOf(WorkspaceState.Ready.class, service.restart(workspaceId).state());

    assertEquals(1, backend.stops);
    assertEquals(1, backend.prepares);
    assertEquals(1, backend.starts);
  }

  @Test
  void resumeRestartsSuspendedWorkspaceOnItsPreservedStorage() {
    Workspace workspace = activeWorkspace();
    workspace.suspend();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    when(tasks.setupStatus(workspaceId))
        .thenReturn(new WorkspaceTaskService.SetupStatus("PENDING", "", "", null, null));

    assertInstanceOf(WorkspaceState.Active.class, service.resume(workspaceId).state());

    assertEquals(1, backend.prepares);
    assertEquals(1, backend.starts);
    verify(workspaces).save(workspace);
    verify(tasks).allowByWorkspace(workspaceId);
    verify(tasks).retrySetup(workspaceId);
    verify(terminals).allowByWorkspace(workspaceId);
  }

  @Test
  void destroyUnregistersPortsAndDestroysBackendStorage() {
    Workspace workspace = activeWorkspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);

    service.destroy(workspaceId);

    verify(ports).unregisterWorkspacePorts(workspaceId);
    assertEquals(1, backend.destroys);
    verify(tasks).closeByWorkspace(workspaceId);
    verify(terminals).closeByWorkspace(workspaceId);
  }

  @Test
  void destroyedWorkspaceCannotStart() {
    Workspace workspace = workspace();
    workspace.destroy();
    when(workspaces.get(workspaceId)).thenReturn(workspace);

    assertThrows(
        dev.jahaziel.workspace.domain.IllegalWorkspaceStateException.class,
        () -> service.start(workspaceId));
    assertInstanceOf(WorkspaceState.Destroyed.class, workspace.state());
    verify(workspaces, never()).save(any());
  }

  @Test
  void readyAndActiveTransitionsArePersisted() {
    Workspace workspace = workspace();
    workspace.prepare();
    workspace.boot();
    when(workspaces.get(workspaceId)).thenReturn(workspace);

    service.markReady(workspaceId);
    assertInstanceOf(WorkspaceState.Ready.class, workspace.state());
    service.markActive(workspaceId);
    assertInstanceOf(WorkspaceState.Active.class, workspace.state());
    verify(workspaces, times(2)).save(workspace);
  }

  private Workspace workspace() {
    return new Workspace(
        workspaceId,
        new UserId(UUID.randomUUID()),
        "project",
        new WorkspaceQuota(21474836480L, 1000000L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()),
            workspaceId,
            new VirtualMachineConfiguration(2, 2048)));
  }

  private Workspace readyWorkspace() {
    Workspace workspace = workspace();
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    return workspace;
  }

  private Workspace activeWorkspace() {
    Workspace workspace = readyWorkspace();
    workspace.start();
    return workspace;
  }

  private static final class FakeBackend implements NodeBackend {
    int prepares;
    int starts;
    int stops;
    int destroys;
    RuntimeException startFailure;

    @Override
    public String name() {
      return "fake";
    }

    @Override
    public WorkspaceHandle prepare(PrepareRequest request) {
      prepares++;
      return new WorkspaceHandle(request.workspaceId(), Path.of("/home"), Path.of("/jail"));
    }

    @Override
    public RuntimeHandle start(RuntimeRequest request) {
      starts++;
      if (startFailure != null) throw startFailure;
      return new RuntimeHandle(request.workspaceId());
    }

    @Override
    public void stop(RuntimeHandle handle, boolean graceful) {
      stops++;
    }

    @Override
    public void destroy(WorkspaceHandle handle) {
      destroys++;
    }
  }
}
