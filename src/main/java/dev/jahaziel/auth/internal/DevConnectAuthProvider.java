/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.auth.internal;

import dev.jahaziel.auth.api.AuthProvider;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.spi.UserIdentity;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default provider: verifies platform JWTs, then loads the account. */
@Component
@ConditionalOnProperty(
    name = "jahaziel.auth.provider",
    havingValue = "devconnect",
    matchIfMissing = true)
public final class DevConnectAuthProvider implements AuthProvider {
  private final JwtService tokens;
  private final UserService users;

  public DevConnectAuthProvider(JwtService tokens, UserService users) {
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.users = Objects.requireNonNull(users, "users");
  }

  @Override
  public String name() {
    return "devconnect";
  }

  @Override
  public UserIdentity authenticate(String token) {
    return users.get(tokens.verify(token).user());
  }

  @Override
  public Optional<UserIdentity> findById(String externalId) {
    try {
      return Optional.of(users.get(new UserId(UUID.fromString(externalId))));
    } catch (RuntimeException missing) {
      return Optional.empty();
    }
  }
}
