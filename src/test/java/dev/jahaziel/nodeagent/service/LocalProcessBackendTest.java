/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.nodeagent.storage.NioCommandRunner;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.PrepareRequest;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.RuntimeHandle;
import dev.jahaziel.spi.RuntimeRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior of {@link LocalProcessBackend} against real folders. */
class LocalProcessBackendTest {
  @TempDir private Path root;

  private LocalProcessBackend backend;
  private UUID workspaceId;
  private UUID ownerId;

  @BeforeEach
  void setUp() {
    backend = new LocalProcessBackend(new FolderStorage(new NioCommandRunner(), root.toString()));
    workspaceId = UUID.randomUUID();
    ownerId = UUID.randomUUID();
  }

  @Test
  void prepareStartStopDestroyUseRealFolders() throws Exception {
    var handle =
        backend.prepare(
            new PrepareRequest(
                workspaceId,
                ownerId,
                new ImageRef("base-image:2026.09"),
                new QuotaBytes(1 << 20),
                2,
                2048));
    assertTrue(Files.isDirectory(handle.homePath()));
    assertTrue(Files.isDirectory(handle.jailPath()));
    assertFalse(backend.isRunning(workspaceId));

    RuntimeHandle runtime =
        backend.start(
            new RuntimeRequest(workspaceId, 2, 2048, 0, 0, 0, 5000, 4096, List.of("a=b")));
    assertEquals(workspaceId, runtime.value());
    assertTrue(backend.isRunning(workspaceId));
    Path descriptor = handle.jailPath().resolve("runtime.json");
    assertTrue(Files.isRegularFile(descriptor));
    assertTrue(Files.readString(descriptor).contains("a=b"));

    backend.stop(runtime, true);
    assertFalse(backend.isRunning(workspaceId));

    Path home = handle.homePath();
    backend.destroy(handle);
    assertFalse(backend.isRunning(workspaceId));
    assertFalse(Files.exists(handle.jailPath()));
    assertTrue(Files.isDirectory(home));
  }

  @Test
  void startWithoutPrepareThrows() {
    RuntimeRequest request =
        new RuntimeRequest(UUID.randomUUID(), 2, 2048, 0, 0, 0, 5000, 4096, List.of());
    assertThrows(IllegalStateException.class, () -> backend.start(request));
  }

  @Test
  void validatesInput() {
    assertThrows(NullPointerException.class, () -> backend.prepare(null));
    assertThrows(NullPointerException.class, () -> backend.start(null));
    assertThrows(NullPointerException.class, () -> backend.stop(null, true));
    assertThrows(NullPointerException.class, () -> backend.destroy(null));
    assertThrows(NullPointerException.class, () -> backend.isRunning(null));
  }
}
