/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import dev.jahaziel.nodeagent.service.LocalProcessBackend;
import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.WorkspaceStorage;
import dev.jahaziel.workspace.internal.WorkspaceBackendConfig;
import org.junit.jupiter.api.Test;

class AppConfigCoverageGapTest {
  private final WorkspaceBackendConfig config = new WorkspaceBackendConfig();

  @Test
  void selectsTheLocalBackendExplicitly() {
    WorkspaceStorage storage = mock(WorkspaceStorage.class);

    NodeBackend backend = config.nodeBackend("local", storage);

    assertInstanceOf(LocalProcessBackend.class, backend);
    assertEquals("local", backend.name());
  }

  @Test
  void rejectsUnknownBackendAtStartup() {
    assertThrows(
        IllegalArgumentException.class,
        () -> config.nodeBackend("unknown", mock(WorkspaceStorage.class)));
  }

  @Test
  void providesFolderStorageForTheSingleServer() {
    assertInstanceOf(FolderStorage.class, config.workspaceStorage("data"));
  }
}
