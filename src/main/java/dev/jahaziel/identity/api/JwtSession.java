/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import dev.jahaziel.identity.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A verified session token.
 *
 * @param user the subject user
 * @param workspaceId the workspace, empty for user-only tokens
 * @param scopes the granted scopes
 * @param expiresAt when the token expires
 */
public record JwtSession(
    UserId user, Optional<UUID> workspaceId, List<String> scopes, Instant expiresAt) {
  public JwtSession {
    Objects.requireNonNull(user, "user");
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(scopes, "scopes");
    Objects.requireNonNull(expiresAt, "expiresAt");
    scopes = List.copyOf(scopes);
  }
}
