/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.nodeagent.service.LocalProcessBackend;
import dev.jahaziel.nodeagent.storage.FolderStorage;
import dev.jahaziel.nodeagent.storage.NioCommandRunner;
import dev.jahaziel.spi.NodeBackend;
import dev.jahaziel.spi.WorkspaceStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single-server runtime wiring. The selected backend and storage implementation are created once
 * and shared by workspace lifecycle, snapshots, and the node facade.
 */
@Configuration
public class WorkspaceBackendConfig {
  @Bean
  public WorkspaceStorage workspaceStorage(
      @Value("${jahaziel.storage.root:data}") String storageRoot) {
    return new FolderStorage(new NioCommandRunner(), storageRoot);
  }

  @Bean
  public NodeBackend nodeBackend(
      @Value("${JAHAZIEL_BACKEND:${jahaziel.workspace.backend:local}}") String backend,
      WorkspaceStorage storage) {
    if (!"local".equalsIgnoreCase(backend.strip())) {
      throw new IllegalArgumentException(
          "only the local workspace backend is available in this release; "
              + "Firecracker remains experimental and is not selectable");
    }
    return new LocalProcessBackend(storage);
  }
}
