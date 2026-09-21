/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.auth.internal;

import dev.jahaziel.auth.api.AuthProvider;
import dev.jahaziel.spi.UserIdentity;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local-development provider. Accepts any token as a fixed admin user. */
@Component
@ConditionalOnProperty(name = "jahaziel.auth.provider", havingValue = "stub")
public final class StubAuthProvider implements AuthProvider {
  private static final UserIdentity STUB =
      new UserIdentity(
          "stub-user",
          "dev@example.com",
          "Local Dev",
          List.of("ROLE_USER", "ROLE_ADMIN"),
          List.of());

  @Override
  public String name() {
    return "stub";
  }

  @Override
  public UserIdentity authenticate(String token) {
    return STUB;
  }

  @Override
  public Optional<UserIdentity> findById(String externalId) {
    return STUB.externalId().equals(externalId) ? Optional.of(STUB) : Optional.empty();
  }
}
