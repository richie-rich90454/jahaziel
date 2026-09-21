/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.auth.api;

import dev.jahaziel.spi.UserIdentity;
import java.util.Optional;

/** Pluggable authentication. DevConnect by default, stub for local development. */
public interface AuthProvider {
  /** Returns the provider name, for example {@code devconnect}. */
  String name();

  /** Authenticates a token. */
  UserIdentity authenticate(String token);

  /** Finds a user by external identifier. */
  Optional<UserIdentity> findById(String externalId);
}
