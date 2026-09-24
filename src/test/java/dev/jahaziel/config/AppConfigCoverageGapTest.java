/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.runtime.api.NodeAgentUnreachableException;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppConfigCoverageGapTest {
  private final AppConfig config = new AppConfig();

  @Test
  void backendRefusesEverything() {
    NodeBackend backend = config.backend();
    assertEquals("unreachable", backend.name());
    var prepare =
        new PrepareRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ImageRef("base:1"),
            new QuotaBytes(1024),
            2,
            2048);
    assertThrows(NodeAgentUnreachableException.class, () -> backend.prepare(prepare));
    var start =
        new RuntimeRequest(UUID.randomUUID(), 2, 2048, 1000, 1000, 1000, 10, 100, List.of());
    assertThrows(NodeAgentUnreachableException.class, () -> backend.start(start));
    assertThrows(
        NodeAgentUnreachableException.class,
        () -> backend.stop(new RuntimeHandle(UUID.randomUUID()), true));
    assertThrows(
        NodeAgentUnreachableException.class,
        () ->
            backend.destroy(
                new WorkspaceHandle(UUID.randomUUID(), Path.of("home"), Path.of("jail"))));
  }

  @Test
  void storageRefusesEverything() {
    WorkspaceStorage storage = config.storage();
    var handle = new WorkspaceHandle(UUID.randomUUID(), Path.of("home"), Path.of("jail"));
    assertThrows(
        NodeAgentUnreachableException.class,
        () ->
            storage.prepare(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ImageRef("base:1"),
                new QuotaBytes(1024)));
    assertThrows(NodeAgentUnreachableException.class, () -> storage.destroy(handle));
    var snapshot = new SnapshotHandle(UUID.randomUUID(), Path.of("snap"), Instant.now());
    assertThrows(
        NodeAgentUnreachableException.class, () -> storage.snapshot(handle, new SnapshotTag("v1")));
    assertThrows(NodeAgentUnreachableException.class, () -> storage.deleteSnapshot(snapshot));
    assertThrows(NodeAgentUnreachableException.class, () -> storage.restore(handle, snapshot));
    assertThrows(NodeAgentUnreachableException.class, () -> storage.usage(handle));
  }
}
