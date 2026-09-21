/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import dev.jahaziel.identity.domain.UserId;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Issues and verifies short-lived session tokens. RS256 with platform keys. */
public interface JwtService {
  /**
   * Issues a token.
   *
   * @param user the subject user
   * @param workspaceId the workspace, or null for user-only tokens
   * @param scopes the granted scopes
   * @param ttl the time to live
   * @return the compact token
   */
  String issue(UserId user, UUID workspaceId, List<String> scopes, Duration ttl);

  /**
   * Verifies a token.
   *
   * @param token the compact token
   * @return the verified session
   * @throws JwtRejectedException when the token is invalid, expired, or misissued
   */
  JwtSession verify(String token);
}
