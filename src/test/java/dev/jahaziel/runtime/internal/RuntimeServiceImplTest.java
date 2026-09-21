/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.runtime.domain.VirtualMachine;
import dev.jahaziel.runtime.domain.VirtualMachineConfiguration;
import dev.jahaziel.runtime.domain.VirtualMachineId;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link RuntimeServiceImpl} with a recording backend. */
class RuntimeServiceImplTest {
  private FakeBackend backend;
  private WorkspaceService workspaces;
  private RuntimeService service;
  private WorkspaceId workspaceId;

  @BeforeEach
  void setUp() {
    backend = new FakeBackend();
    workspaces = mock(WorkspaceService.class);
    service = new RuntimeServiceImpl(backend, workspaces, new ImageRef("base-image:2026.09"));
    workspaceId = new WorkspaceId(UUID.randomUUID());
  }

  @Test
  void startPreparesBootsAndSavesTwice() {
    var workspace = realWorkspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    var started = service.start(workspaceId);
    assertInstanceOf(WorkspaceState.Booting.class, started.state());
    assertEquals(1, backend.prepares);
    assertEquals(1, backend.starts);
    assertEquals(0, backend.stops);
    verify(workspaces, times(2)).save(workspace);
  }

  @Test
  void stopSuspendsRunning() {
    var workspace = realWorkspace();
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    var stopped = service.stop(workspaceId);
    assertInstanceOf(WorkspaceState.Suspended.class, stopped.state());
    verify(workspaces).save(workspace);
  }

  @Test
  void stopIsIdempotentWhenSuspended() {
    var workspace = realWorkspace();
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
    workspace.suspend();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    service.stop(workspaceId);
    assertEquals(0, backend.prepares);
    assertEquals(0, backend.starts);
    assertEquals(0, backend.stops);
  }

  @Test
  void restartStopsThenStarts() {
    var workspace = realWorkspace();
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    var restarted = service.restart(workspaceId);
    assertInstanceOf(WorkspaceState.Booting.class, restarted.state());
    assertEquals(1, backend.prepares);
    assertEquals(1, backend.starts);
    assertEquals(1, backend.stops);
  }

  @Test
  void readyAndActiveTransitionsPersist() {
    var workspace = realWorkspace();
    workspace.prepare();
    workspace.boot();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    service.markReady(workspaceId);
    assertInstanceOf(WorkspaceState.Ready.class, workspace.state());
    service.markActive(workspaceId);
    assertInstanceOf(WorkspaceState.Active.class, workspace.state());
    verify(workspaces, times(2)).save(workspace);
  }

  @Test
  void resumeBootsFreshVmOnPreservedHome() {
    var workspace = realWorkspace();
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
    workspace.suspend();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    var resumed = service.resume(workspaceId);
    assertInstanceOf(WorkspaceState.Active.class, resumed.state());
    assertEquals(0, backend.prepares);
    assertEquals(1, backend.starts);
    verify(workspaces).save(workspace);
  }

  private Workspace realWorkspace() {
    return new Workspace(
        workspaceId,
        new UserId(UUID.randomUUID()),
        "club",
        new WorkspaceQuota(21474836480L, 1000000L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()),
            workspaceId,
            new VirtualMachineConfiguration(2, 2048)));
  }

  /** In-memory node backend recording calls. */
  static final class FakeBackend implements NodeBackend {
    int prepares;
    int starts;
    int stops;

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
      return new RuntimeHandle(UUID.randomUUID());
    }

    @Override
    public void stop(RuntimeHandle handle, boolean graceful) {
      stops++;
    }

    @Override
    public void destroy(WorkspaceHandle handle) {}
  }
}
