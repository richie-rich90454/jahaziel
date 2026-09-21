/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import dev.jahaziel.spi.NodeBackend;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Selects the workspace backend. {@code local} by default; {@code firecracker} on Linux with
 * {@code /dev/kvm}. Primary so it wins over the fail-closed stub in AppConfig.
 */
@Configuration
@ConditionalOnProperty(
    name = "jahaziel.dist.enabled",
    havingValue = "false",
    matchIfMissing = true)
public class WorkspaceBackendConfig {
  @Bean
  @Primary
  public NodeBackend nodeBackend(
      @Value("${jahaziel.workspace.backend:local}") String backend,
      @Value("${jahaziel.storage.workspaces-dir:data/workspaces}") String workspacesDir) {
    if ("firecracker".equalsIgnoreCase(backend.strip())) {
      return new FirecrackerBackend(Path.of(workspacesDir));
    }
    return new LocalBackend(Path.of(workspacesDir));
  }
}
