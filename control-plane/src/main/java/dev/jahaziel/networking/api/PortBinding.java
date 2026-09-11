/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A registered port binding.
 *
 * @param userId the owning user
 * @param port the bound port
 * @param workspaceId the serving workspace
 * @param protocol one of http, https, tcp
 * @param registeredAt when the binding was registered
 */
public record PortBinding(UUID userId, int port, UUID workspaceId, String protocol, Instant registeredAt) {
  private static final Set<String> PROTOCOLS = Set.of("http", "https", "tcp");

  public PortBinding {
    Objects.requireNonNull(userId, "userId");
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("port must be between 1 and 65535");
    }
    Objects.requireNonNull(workspaceId, "workspaceId");
    if (protocol == null || !PROTOCOLS.contains(protocol)) {
      throw new IllegalArgumentException("protocol must be one of http, https, tcp");
    }
    Objects.requireNonNull(registeredAt, "registeredAt");
  }
}
