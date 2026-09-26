/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.runtime.internal.RuntimeServiceImpl;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import dev.jahaziel.workspace.domain.WorkspaceState;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeExtraCoverageTest {
  private Workspace fresh(WorkspaceId id) {
    return new Workspace(
        id,
        new UserId(UUID.randomUUID()),
        "c",
        new WorkspaceQuota(21474836480L, 1000000L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()), id, new VirtualMachineConfiguration(2, 2048)));
  }

  @Test
  void stopReturnsEarlyForRequestedAndDestroyed() {
    NodeBackend backend = mock(NodeBackend.class);
    WorkspaceService ws = mock(WorkspaceService.class);
    RuntimeServiceImpl svc =
        new RuntimeServiceImpl(
            backend,
            ws,
            new ImageRef("b:1"),
            mock(dev.jahaziel.ide.internal.IdeServerService.class),
            mock(dev.jahaziel.networking.api.PortService.class),
            mock(dev.jahaziel.runtime.internal.BootProgressService.class));
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    Workspace requested = fresh(id);
    when(ws.get(id)).thenReturn(requested);
    assertEquals(requested, svc.stop(id));
    verify(backend, times(0))
        .stop(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    Workspace suspended = fresh(id);
    suspended.prepare();
    suspended.boot();
    suspended.abortBoot();
    when(ws.get(id)).thenReturn(suspended);
    svc.stop(id);
    Workspace destroyed = fresh(id);
    destroyed.destroy();
    when(ws.get(id)).thenReturn(destroyed);
    svc.stop(id);
  }

  @Test
  void stopAbortsBootWhenSuspendFails() {
    NodeBackend backend = mock(NodeBackend.class);
    WorkspaceService ws = mock(WorkspaceService.class);
    RuntimeServiceImpl svc =
        new RuntimeServiceImpl(
            backend,
            ws,
            new ImageRef("b:1"),
            mock(dev.jahaziel.ide.internal.IdeServerService.class),
            mock(dev.jahaziel.networking.api.PortService.class),
            mock(dev.jahaziel.runtime.internal.BootProgressService.class));
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    Workspace booting = fresh(id);
    booting.prepare();
    booting.boot();
    when(ws.get(id)).thenReturn(booting);
    var stopped = svc.stop(id);
    assertEquals(WorkspaceState.Suspended.class, stopped.state().getClass());
    verify(backend).stop(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(true));
  }

  @Test
  void ctorGuards() {
    WorkspaceService ws = mock(WorkspaceService.class);
    NodeBackend b = mock(NodeBackend.class);
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class,
        () ->
            new RuntimeServiceImpl(
                null,
                ws,
                new ImageRef("b:1"),
                mock(dev.jahaziel.ide.internal.IdeServerService.class),
                mock(dev.jahaziel.networking.api.PortService.class),
                mock(dev.jahaziel.runtime.internal.BootProgressService.class)));
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class,
        () ->
            new RuntimeServiceImpl(
                b,
                null,
                new ImageRef("b:1"),
                mock(dev.jahaziel.ide.internal.IdeServerService.class),
                mock(dev.jahaziel.networking.api.PortService.class),
                mock(dev.jahaziel.runtime.internal.BootProgressService.class)));
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class,
        () ->
            new RuntimeServiceImpl(
                b,
                ws,
                null,
                mock(dev.jahaziel.ide.internal.IdeServerService.class),
                mock(dev.jahaziel.networking.api.PortService.class),
                mock(dev.jahaziel.runtime.internal.BootProgressService.class)));
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class,
        () ->
            new RuntimeServiceImpl(
                b,
                ws,
                new ImageRef("b:1"),
                null,
                mock(dev.jahaziel.networking.api.PortService.class),
                mock(dev.jahaziel.runtime.internal.BootProgressService.class)));
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class,
        () ->
            new RuntimeServiceImpl(
                b,
                ws,
                new ImageRef("b:1"),
                mock(dev.jahaziel.ide.internal.IdeServerService.class),
                null,
                mock(dev.jahaziel.runtime.internal.BootProgressService.class)));
    org.junit.jupiter.api.Assertions.assertThrows(
        NullPointerException.class,
        () ->
            new RuntimeServiceImpl(
                b,
                ws,
                new ImageRef("b:1"),
                mock(dev.jahaziel.ide.internal.IdeServerService.class),
                mock(dev.jahaziel.networking.api.PortService.class),
                null));
  }
}
