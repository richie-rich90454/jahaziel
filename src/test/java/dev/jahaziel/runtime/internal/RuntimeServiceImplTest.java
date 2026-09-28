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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jahaziel.ide.internal.IdeServerService;
import dev.jahaziel.ide.internal.IdeServerService.BootPhase;
import dev.jahaziel.ide.internal.RehNotInstalledException;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.nodeagent.service.LocalProcessBackend;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link RuntimeServiceImpl} with recording and local backends. */
class RuntimeServiceImplTest {
  private FakeBackend backend;
  private WorkspaceService workspaces;
  private IdeServerService ideServers;
  private PortService ports;
  private BootProgressService progress;
  private RuntimeService service;
  private WorkspaceId workspaceId;

  @TempDir private Path tmp;

  @BeforeEach
  void setUp() {
    backend = new FakeBackend();
    workspaces = mock(WorkspaceService.class);
    ideServers = mock(IdeServerService.class);
    ports = mock(PortService.class);
    progress = mock(BootProgressService.class);
    service =
        new RuntimeServiceImpl(
            backend, workspaces, new ImageRef("base-image:2026.09"), ideServers, ports, progress);
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
  void startFailureParksWorkspaceAsFailedWithReason() {
    backend.startFailure = new IllegalStateException("disk gone");
    var workspace = realWorkspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    assertThrows(IllegalStateException.class, () -> service.start(workspaceId));
    assertInstanceOf(WorkspaceState.Failed.class, workspace.state());
    assertEquals("disk gone", ((WorkspaceState.Failed) workspace.state()).reason());
    verify(progress).clear(workspaceId);
    verify(workspaces, times(2)).save(workspace);
  }

  @Test
  void startFailureWithoutMessageFallsBackToClassName() {
    backend.startFailure = new IllegalStateException();
    var workspace = realWorkspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    assertThrows(IllegalStateException.class, () -> service.start(workspaceId));
    assertEquals("IllegalStateException", ((WorkspaceState.Failed) workspace.state()).reason());
  }

  @Test
  void startFailureWithBlankMessageFallsBackToClassName() {
    backend.startFailure = new IllegalStateException("  ");
    var workspace = realWorkspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    assertThrows(IllegalStateException.class, () -> service.start(workspaceId));
    assertEquals("IllegalStateException", ((WorkspaceState.Failed) workspace.state()).reason());
  }

  @Test
  void startOnDestroyedKeepsDestroyedState() {
    var workspace = realWorkspace();
    workspace.destroy();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    assertThrows(
        dev.jahaziel.workspace.domain.IllegalWorkspaceStateException.class,
        () -> service.start(workspaceId));
    assertInstanceOf(WorkspaceState.Destroyed.class, workspace.state());
    verify(workspaces, never()).save(any());
  }

  @Test
  void destroyStopsIdeClearsBindingsAndBackend() {
    var workspace = realWorkspace();
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    service.destroy(workspaceId);
    assertInstanceOf(WorkspaceState.Suspended.class, workspace.state());
    verify(ideServers).stop(workspaceId);
    verify(progress).clear(workspaceId);
    verify(ports).unregisterWorkspacePorts(workspaceId);
    assertEquals(1, backend.destroys);
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
    assertEquals(1, backend.prepares);
    assertEquals(1, backend.starts);
    verify(workspaces).save(workspace);
  }

  @Test
  void startFreshLaunchesIdeBindsPortAndMarksReady() throws Exception {
    Path home = Files.createDirectory(tmp.resolve("fresh-home"));
    LocalProcessBackend local = localBackend(home);
    when(ideServers.ensureRunning(eq(workspaceId), eq(home), any())).thenReturn(19871);
    when(ports.bindingsByWorkspace(workspaceId)).thenReturn(List.of());
    var workspace = realWorkspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    RuntimeService localService = localService(local);
    var started = localService.start(workspaceId);
    assertInstanceOf(WorkspaceState.Ready.class, started.state());
    assertTrue(local.isRunning(workspaceId.value()));
    verify(ports).registerPort(workspace.owner(), 19871, workspaceId, "http");
    verify(workspaces, times(3)).save(workspace);
    verify(progress).report(workspaceId, BootPhase.PREPARING);
    verify(progress).report(workspaceId, BootPhase.REGISTERING);
    verify(progress).report(workspaceId, BootPhase.READY);
  }

  @Test
  void startHealsInterruptedBoot() throws Exception {
    Path home = Files.createDirectory(tmp.resolve("heal-home"));
    LocalProcessBackend local = localBackend(home);
    when(ideServers.ensureRunning(eq(workspaceId), eq(home), any())).thenReturn(19872);
    when(ports.bindingsByWorkspace(workspaceId)).thenReturn(List.of());
    var workspace = realWorkspace();
    workspace.prepare();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    var started = localService(local).start(workspaceId);
    assertInstanceOf(WorkspaceState.Ready.class, started.state());
  }

  @Test
  void startIsIdempotentFromBootingReadyAndActive() throws Exception {
    for (String drive : List.of("booting", "ready", "active")) {
      Path home = Files.createDirectory(tmp.resolve(drive + "-home"));
      LocalProcessBackend local = localBackend(home);
      when(ideServers.ensureRunning(eq(workspaceId), eq(home), any())).thenReturn(19873);
      when(ports.bindingsByWorkspace(workspaceId)).thenReturn(List.of());
      var workspace = realWorkspace();
      workspace.prepare();
      workspace.boot();
      if (!drive.equals("booting")) {
        workspace.markReady();
      }
      if (drive.equals("active")) {
        workspace.start();
      }
      when(workspaces.get(workspaceId)).thenReturn(workspace);
      var restarted = localService(local).start(workspaceId);
      if (drive.equals("booting")) {
        assertInstanceOf(WorkspaceState.Ready.class, restarted.state());
      } else {
        assertEquals(workspace.state().getClass(), restarted.state().getClass());
      }
    }
  }

  @Test
  void startFromSuspendedStillConflicts() throws Exception {
    var workspace = realWorkspace();
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
    workspace.suspend();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    Path home = Files.createDirectory(tmp.resolve("suspended-home"));
    RuntimeService localService = localService(localBackend(home));
    assertThrows(
        dev.jahaziel.workspace.domain.IllegalWorkspaceStateException.class,
        () -> localService.start(workspaceId));
    verifyNoInteractions(ideServers);
  }

  @Test
  void startFromBootingOnForeignBackendStillConflicts() {
    var workspace = realWorkspace();
    workspace.prepare();
    workspace.boot();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    assertThrows(
        dev.jahaziel.workspace.domain.IllegalWorkspaceStateException.class,
        () -> service.start(workspaceId));
  }

  @Test
  void startSkipsBackendRestartWhenAlreadyRunning() throws Exception {
    Path home = Files.createDirectory(tmp.resolve("rerun-home"));
    LocalProcessBackend local = localBackend(home);
    when(ideServers.ensureRunning(eq(workspaceId), eq(home), any())).thenReturn(19874);
    when(ports.bindingsByWorkspace(workspaceId)).thenReturn(List.of());
    var workspace = realWorkspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    RuntimeService localService = localService(local);
    localService.start(workspaceId);
    var again = localService.start(workspaceId);
    assertInstanceOf(WorkspaceState.Ready.class, again.state());
    verify(ideServers, times(2)).ensureRunning(eq(workspaceId), eq(home), any());
  }

  @Test
  void startReusesLiveIdePortBinding() throws Exception {
    Path home = Files.createDirectory(tmp.resolve("reuse-home"));
    LocalProcessBackend local = localBackend(home);
    var workspace = realWorkspace();
    try (var listener = new ServerSocket(0)) {
      int port = listener.getLocalPort();
      when(ideServers.ensureRunning(eq(workspaceId), eq(home), any())).thenReturn(port);
      when(ports.bindingsByWorkspace(workspaceId))
          .thenReturn(
              List.of(
                  new PortBinding(
                      workspace.owner().value(),
                      port,
                      workspaceId.value(),
                      "http",
                      Instant.now())));
      when(workspaces.get(workspaceId)).thenReturn(workspace);
      var started = localService(local).start(workspaceId);
      assertInstanceOf(WorkspaceState.Ready.class, started.state());
      verify(ports, never()).registerPort(workspace.owner(), port, workspaceId, "http");
      verify(ports, never()).unregisterPort(workspace.owner(), port);
    }
  }

  @Test
  void startCleansDeadBindingsBeforeRegistering() throws Exception {
    Path home = Files.createDirectory(tmp.resolve("clean-home"));
    LocalProcessBackend local = localBackend(home);
    var workspace = realWorkspace();
    when(ideServers.ensureRunning(eq(workspaceId), eq(home), any())).thenReturn(19875);
    when(ports.bindingsByWorkspace(workspaceId))
        .thenReturn(
            List.of(
                new PortBinding(
                    workspace.owner().value(), 19876, workspaceId.value(), "http", Instant.now())));
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    localService(local).start(workspaceId);
    verify(ports).unregisterPort(workspace.owner(), 19876);
    verify(ports).registerPort(workspace.owner(), 19875, workspaceId, "http");
  }

  @Test
  void stopKillsIdeServer() {
    var workspace = realWorkspace();
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    service.stop(workspaceId);
    verify(ideServers).stop(workspaceId);
    verify(progress).clear(workspaceId);
  }

  @Test
  void resumeEnsuresIdeOnLocalBackend() throws Exception {
    Path home = Files.createDirectory(tmp.resolve("resume-home"));
    LocalProcessBackend local = localBackend(home);
    when(ideServers.ensureRunning(eq(workspaceId), eq(home), any())).thenReturn(19877);
    when(ports.bindingsByWorkspace(workspaceId)).thenReturn(List.of());
    var workspace = realWorkspace();
    workspace.prepare();
    workspace.boot();
    workspace.markReady();
    workspace.start();
    workspace.suspend();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    var resumed = localService(local).resume(workspaceId);
    assertInstanceOf(WorkspaceState.Active.class, resumed.state());
    verify(ideServers).ensureRunning(eq(workspaceId), eq(home), any());
    verify(ports).registerPort(workspace.owner(), 19877, workspaceId, "http");
  }

  @Test
  void startDegradesWithoutInstalledReh() throws Exception {
    Path home = Files.createDirectory(tmp.resolve("no-reh-home"));
    LocalProcessBackend local = localBackend(home);
    when(ideServers.ensureRunning(eq(workspaceId), eq(home), any()))
        .thenThrow(new RehNotInstalledException(home.resolve("missing.tar.zst")));
    when(ports.bindingsByWorkspace(workspaceId)).thenReturn(List.of());
    var workspace = realWorkspace();
    when(workspaces.get(workspaceId)).thenReturn(workspace);
    var started = localService(local).start(workspaceId);
    assertInstanceOf(WorkspaceState.Booting.class, started.state());
    verify(ports, never()).registerPort(workspace.owner(), 0, workspaceId, "http");
  }

  private RuntimeService localService(LocalProcessBackend local) {
    return new RuntimeServiceImpl(
        local, workspaces, new ImageRef("base-image:2026.09"), ideServers, ports, progress);
  }

  private LocalProcessBackend localBackend(Path home) throws Exception {
    Files.createDirectories(home);
    WorkspaceStorage storage = mock(WorkspaceStorage.class);
    when(storage.prepare(any(), any(), any(), any()))
        .thenReturn(new WorkspaceHandle(workspaceId.value(), home, home));
    return new LocalProcessBackend(storage);
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
      if (startFailure != null) {
        throw startFailure;
      }
      starts++;
      return new RuntimeHandle(UUID.randomUUID());
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
