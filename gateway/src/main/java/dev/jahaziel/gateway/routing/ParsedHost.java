/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.routing;

import java.util.Objects;

/**
 * A parsed wildcard host.
 *
 * @param userSlug the URL-safe user slug
 * @param port the workspace port
 */
public record ParsedHost(String userSlug, int port) {
  public ParsedHost {
    if (userSlug == null || userSlug.isBlank()) {
      throw new IllegalArgumentException("userSlug must not be blank");
    }
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("port must be between 1 and 65535");
    }
    Objects.requireNonNull(userSlug, "userSlug");
  }
}
