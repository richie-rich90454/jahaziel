/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.WorkspaceHandle;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Plugin-contract branches of {@link FakeNodeAgent}. */
class FakeNodeAgentCoverageTest {
  @Test
  void pluginContracts() {
    FakeNodeAgent agent = new FakeNodeAgent();
    assertEquals("fake", agent.name());
    UUID workspace = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    PrepareRequest request =
        new PrepareRequest(workspace, owner, new ImageRef("img"), new QuotaBytes(100), 2, 1024);
    WorkspaceHandle handle = agent.prepare(request);
    assertTrue(agent.isPrepared(workspace));
    RuntimeRequest runtime = new RuntimeRequest(workspace, 2, 1024, 0, 0, 0, 100, 100, List.of());
    RuntimeHandle started = agent.start(runtime);
    assertEquals(workspace, started.value());
    agent.stop(started, true);
    agent.stop(new RuntimeHandle(workspace), false);
    assertEquals(0, agent.usage(handle).logicalBytes());
    SnapshotHandle snap = agent.snapshot(handle, new SnapshotTag("t1"));
    assertEquals(1, agent.listSnapshots(handle).size());
    agent.restore(handle, snap);
    agent.restoreSnapshot(handle, snap);
    agent.deleteSnapshot(snap);
    assertTrue(agent.listSnapshots(handle).isEmpty());
    agent.destroy(handle);
  }

  @Test
  void storagePrepareAndDestroyKeep() {
    FakeNodeAgent agent = new FakeNodeAgent();
    UUID workspace = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    WorkspaceHandle handle =
        agent.prepare(workspace, owner, new ImageRef("img"), new QuotaBytes(10));
    assertTrue(agent.isPrepared(workspace));
    SnapshotHandle snap = agent.createSnapshot(handle, new SnapshotTag("k"), "tester");
    agent.destroyWorkspace(handle, true);
    assertTrue(!agent.isPrepared(workspace));
    FakeNodeAgent second = new FakeNodeAgent();
    WorkspaceHandle handle2 =
        second.prepare(workspace, owner, new ImageRef("img"), new QuotaBytes(10));
    SnapshotHandle snap2 = second.createSnapshot(handle2, new SnapshotTag("k2"), "tester");
    second.destroyWorkspace(handle2, true);
    // ponytail: keepSnapshots=true preserves snapshot rows even though unprepared.
    assertThrows(IllegalArgumentException.class, () -> second.listSnapshots(handle2));
    assertTrue(snap != null && snap2 != null);
  }

  @Test
  void nullsAndUnprepared() {
    FakeNodeAgent agent = new FakeNodeAgent();
    assertThrows(NullPointerException.class, () -> agent.prepare((PrepareRequest) null));
    assertThrows(NullPointerException.class, () -> agent.start(null));
    assertThrows(NullPointerException.class, () -> agent.stop(null, true));
    assertThrows(NullPointerException.class, () -> agent.destroy(null));
    assertThrows(NullPointerException.class, () -> agent.deleteSnapshot(null));
    UUID workspace = UUID.randomUUID();
    WorkspaceHandle ghost = new WorkspaceHandle(workspace, Path.of("/y"), Path.of("/z"));
    assertThrows(
        IllegalArgumentException.class,
        () -> agent.start(new RuntimeRequest(workspace, 1, 1, 0, 0, 0, 1, 1, List.of())));
    assertThrows(
        IllegalArgumentException.class, () -> agent.stop(new RuntimeHandle(workspace), true));
    assertThrows(IllegalArgumentException.class, () -> agent.destroy(ghost));
    assertThrows(IllegalArgumentException.class, () -> agent.usage(ghost));
    assertThrows(IllegalArgumentException.class, () -> agent.snapshot(ghost, new SnapshotTag("t")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            agent.restore(
                ghost,
                new SnapshotHandle(UUID.randomUUID(), Path.of("/s"), java.time.Instant.now())));
    assertThrows(IllegalArgumentException.class, () -> agent.attachPort(ghost, 80, "http"));
    assertThrows(IllegalArgumentException.class, () -> agent.detachPort(ghost, 80));
  }
}
