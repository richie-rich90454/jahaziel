/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.auth;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A verified gateway session.
 *
 * @param userId the user identifier
 * @param workspaceId the workspace identifier
 * @param scopes the granted scopes
 */
public record GatewaySession(UUID userId, UUID workspaceId, List<String> scopes) {
  public GatewaySession {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(scopes, "scopes");
    scopes = List.copyOf(scopes);
  }
}
