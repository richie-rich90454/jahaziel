/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.nodeagent.audit.AuditSpool;
import dev.jahaziel.nodeagent.sync.SyncServer;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.WorkspaceHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Remaining branches of {@link NodeAgentService}. */
class NodeAgentServiceCoverageTest {
  @TempDir private Path root;

  @Test
  void constructorRejectsNulls() {
    NodeBackend backend = mock(NodeBackend.class);
    dev.jahaziel.spi.WorkspaceStorage storage = mock(dev.jahaziel.spi.WorkspaceStorage.class);
    AuditSpool spool = new AuditSpool(root);
    SyncServer sync = mock(SyncServer.class);
    assertThrows(
        NullPointerException.class, () -> new NodeAgentService(null, storage, spool, sync));
    assertThrows(
        NullPointerException.class, () -> new NodeAgentService(backend, null, spool, sync));
    assertThrows(
        NullPointerException.class, () -> new NodeAgentService(backend, storage, null, sync));
    assertThrows(
        NullPointerException.class, () -> new NodeAgentService(backend, storage, spool, null));
  }

  @Test
  void stopAndDestroyWithSnapshots() throws Exception {
    NodeBackend backend = mock(NodeBackend.class);
    dev.jahaziel.spi.WorkspaceStorage storage = mock(dev.jahaziel.spi.WorkspaceStorage.class);
    SyncServer sync = mock(SyncServer.class);
    NodeAgentService service = new NodeAgentService(backend, storage, new AuditSpool(root), sync);
    UUID workspace = UUID.randomUUID();
    Path home = root.resolve("home");
    Files.createDirectories(home);
    WorkspaceHandle handle = new WorkspaceHandle(workspace, home, root.resolve("jail"));
    service.stopRuntime(handle, true, 5);
    verify(backend).stop(any(), org.mockito.ArgumentMatchers.eq(true));
    service.stopRuntime(handle, false, 5);
    assertThrows(NullPointerException.class, () -> service.stopRuntime(null, true, 1));
    assertThrows(NullPointerException.class, () -> service.startRuntime(handle, null));
    assertThrows(
        NullPointerException.class,
        () ->
            service.startRuntime(
                null, new RuntimeRequest(workspace, 1, 1, 0, 0, 0, 1, 1, List.of())));
    Path snaps = home.getParent().resolve("snaps").resolve(workspace.toString());
    Files.createDirectories(snaps.resolve("a"));
    Files.createDirectories(snaps.resolve("b"));
    Files.createFile(snaps.resolve("plain.txt"));
    service.destroyWorkspace(handle, false);
    verify(storage, org.mockito.Mockito.times(2)).deleteSnapshot(any());
    assertTrue(spoolText().contains("workspace.destroy"));
  }

  @Test
  void destroyKeepsSnapshots() throws Exception {
    NodeBackend backend = mock(NodeBackend.class);
    dev.jahaziel.spi.WorkspaceStorage storage = mock(dev.jahaziel.spi.WorkspaceStorage.class);
    SyncServer sync = mock(SyncServer.class);
    NodeAgentService service = new NodeAgentService(backend, storage, new AuditSpool(root), sync);
    WorkspaceHandle handle =
        new WorkspaceHandle(UUID.randomUUID(), Path.of("/home"), Path.of("/jail"));
    service.destroyWorkspace(handle, true);
    verify(storage, org.mockito.Mockito.never()).deleteSnapshot(any());
  }

  @Test
  void listEmptyAndNull() throws Exception {
    NodeBackend backend = mock(NodeBackend.class);
    dev.jahaziel.spi.WorkspaceStorage storage = mock(dev.jahaziel.spi.WorkspaceStorage.class);
    SyncServer sync = mock(SyncServer.class);
    NodeAgentService service = new NodeAgentService(backend, storage, new AuditSpool(root), sync);
    assertThrows(NullPointerException.class, () -> service.listSnapshots(null));
    WorkspaceHandle handle =
        new WorkspaceHandle(UUID.randomUUID(), root.resolve("h"), root.resolve("j"));
    assertTrue(service.listSnapshots(handle).isEmpty());
    assertThrows(NullPointerException.class, () -> service.attachPort(null, 80, "http"));
    assertThrows(NullPointerException.class, () -> service.detachPort(null, 80));
  }

  @Test
  void auditEscapesQuotes() throws Exception {
    NodeBackend backend = mock(NodeBackend.class);
    when(backend.prepare(any()))
        .thenReturn(new WorkspaceHandle(UUID.randomUUID(), Path.of("/home"), Path.of("/jail")));
    dev.jahaziel.spi.WorkspaceStorage storage = mock(dev.jahaziel.spi.WorkspaceStorage.class);
    SyncServer sync = mock(SyncServer.class);
    NodeAgentService service = new NodeAgentService(backend, storage, new AuditSpool(root), sync);
    UUID workspace = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    WorkspaceHandle handle = new WorkspaceHandle(workspace, Path.of("/home"), Path.of("/jail"));
    when(backend.prepare(any())).thenReturn(handle);
    service.prepareWorkspace(workspace, owner, "a\"b\\c", 128, 1, new QuotaBytes(10));
    assertTrue(spoolText().contains("a\\\"b\\\\c"));
    SnapshotHandle snap = new SnapshotHandle(UUID.randomUUID(), Path.of("/s"), Instant.now());
    when(storage.snapshot(any(), any())).thenReturn(snap);
    service.createSnapshot(handle, new SnapshotTag("t"), "a\"b");
    service.restoreSnapshot(handle, snap);
    service.deleteSnapshot(snap);
    service.attachPort(handle, 8080, "http");
    service.detachPort(handle, 8080);
    assertEquals(snap, snap);
  }

  private String spoolText() throws Exception {
    return Files.readString(
        Files.list(root)
            .filter(path -> path.toString().endsWith(".jsonl"))
            .findFirst()
            .orElseThrow());
  }
}
