/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.nodeagent.audit.AuditSpool;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link NodeAgentService} with stubbed plugins and a real spool. */
class NodeAgentServiceTest {
  @TempDir private Path root;

  private NodeBackend backend;
  private WorkspaceStorage storage;
  private NodeAgentService service;
  private UUID workspaceId;
  private UUID ownerId;

  @BeforeEach
  void setUp() {
    backend = mock(NodeBackend.class);
    storage = mock(WorkspaceStorage.class);
    service = new NodeAgentService(backend, storage, new AuditSpool(root));
    workspaceId = UUID.randomUUID();
    ownerId = UUID.randomUUID();
  }

  @Test
  void prepareDelegatesAndAudits() {
    WorkspaceHandle handle = handle();
    when(backend.prepare(any())).thenReturn(handle);
    WorkspaceHandle prepared =
        service.prepareWorkspace(
            workspaceId, ownerId, "base-image:2026.09", 2048, 2, new QuotaBytes(1));
    assertEquals(handle, prepared);
    assertTrue(spoolText().contains("workspace.prepare"));
  }

  @Test
  void snapshotsDelegate() {
    WorkspaceHandle handle = handle();
    SnapshotHandle snap = new SnapshotHandle(UUID.randomUUID(), Path.of("/snaps/x"), Instant.now());
    when(storage.snapshot(any(), any())).thenReturn(snap);
    assertEquals(snap, service.createSnapshot(handle, new SnapshotTag("daily"), "system"));
    service.restoreSnapshot(handle, snap);
    verify(storage).restore(handle, snap);
    service.deleteSnapshot(snap);
    verify(storage).deleteSnapshot(snap);
    assertTrue(spoolText().contains("snapshot.create"));
  }

  @Test
  void portsRegister() {
    WorkspaceHandle handle = handle();
    String vsock = service.attachPort(handle, 8080, "http");
    assertTrue(vsock.contains("8080"));
    service.detachPort(handle, 8080);
    assertTrue(spoolText().contains("port.attach"));
  }

  @Test
  void listsSnapshotsFromDisk() throws Exception {
    WorkspaceHandle handle = handle();
    Path snaps = root.resolve("snaps");
    Files.createDirectories(snaps);
    Files.createDirectory(snaps.resolve("daily"));
    WorkspaceHandle withHome =
        new WorkspaceHandle(workspaceId, root.resolve("home"), handle.jailPath());
    Files.createDirectories(root.resolve("home"));
    List<SnapshotSummary> listed = service.listSnapshots(withHome);
    assertEquals(1, listed.size());
    assertEquals("daily", listed.get(0).tag());
  }

  private WorkspaceHandle handle() {
    return new WorkspaceHandle(workspaceId, Path.of("/home"), Path.of("/jail"));
  }

  private String spoolText() {
    try {
      return Files.readString(
          Files.list(root)
              .filter(path -> path.toString().endsWith(".jsonl"))
              .findFirst()
              .orElseThrow());
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }
}
