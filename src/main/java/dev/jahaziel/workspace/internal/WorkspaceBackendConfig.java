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
import java.nio.file.Path;
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
      @Value("${jahaziel.workspace.backend:local}") String backend,
      WorkspaceStorage storage,
      @Value("${jahaziel.storage.root:data}") String storageRoot) {
    if ("firecracker".equalsIgnoreCase(backend.strip())) {
      return new FirecrackerBackend(Path.of(storageRoot, "workspaces"));
    }
    if (!"local".equalsIgnoreCase(backend.strip())) {
      throw new IllegalArgumentException("unsupported workspace backend: " + backend);
    }
    return new LocalProcessBackend(storage);
  }
}
