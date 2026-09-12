/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.WorkspaceHandle;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link FakeNodeAgent} with no host calls. */
class FakeNodeAgentTest {
  private FakeNodeAgent agent;
  private UUID workspaceId;
  private UUID ownerId;

  @BeforeEach
  void setUp() {
    agent = new FakeNodeAgent();
    workspaceId = UUID.randomUUID();
    ownerId = UUID.randomUUID();
  }

  @Test
  void preparesStartsStopsAndDestroys() {
    WorkspaceHandle handle = prepare();
    assertTrue(agent.isPrepared(workspaceId));
    agent.startRuntime(handle, runtime());
    agent.stopRuntime(handle, true, 30);
    agent.destroyWorkspace(handle, false);
    assertFalse(agent.isPrepared(workspaceId));
  }

  @Test
  void snapshotsListAndDelete() {
    WorkspaceHandle handle = prepare();
    SnapshotHandle snap = agent.createSnapshot(handle, new SnapshotTag("daily"), "system");
    List<SnapshotSummary> listed = agent.listSnapshots(handle);
    assertEquals(1, listed.size());
    assertEquals("daily", listed.get(0).tag());
    agent.deleteSnapshot(snap);
    assertTrue(agent.listSnapshots(handle).isEmpty());
  }

  @Test
  void portsAttachAndDetach() {
    WorkspaceHandle handle = prepare();
    String vsock = agent.attachPort(handle, 8080, "http");
    assertTrue(vsock.contains("8080"));
    agent.detachPort(handle, 8080);
  }

  @Test
  void unpreparedHandlesThrow() {
    WorkspaceHandle handle = new WorkspaceHandle(workspaceId, Path.of("/y"), Path.of("/z"));
    assertThrows(IllegalArgumentException.class, () -> agent.startRuntime(handle, runtime()));
    assertThrows(IllegalArgumentException.class, () -> agent.stopRuntime(handle, true, 30));
    assertThrows(
        IllegalArgumentException.class,
        () -> agent.createSnapshot(handle, new SnapshotTag("t"), "system"));
  }

  @Test
  void summariesValidate() {
    SnapshotHandle snap = new SnapshotHandle(UUID.randomUUID(), Path.of("/s"), Instant.now());
    assertThrows(
        IllegalArgumentException.class, () -> new SnapshotSummary(snap, "  ", Instant.now()));
  }

  private WorkspaceHandle prepare() {
    return agent.prepareWorkspace(
        workspaceId, ownerId, "base-image:2026.09", 2048, 2, new QuotaBytes(1));
  }

  private static RuntimeRequest runtime() {
    return new RuntimeRequest(UUID.randomUUID(), 2, 2048, 0, 0, 0, 5000, 4096, List.of());
  }
}
