/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.util.Map;
import java.util.Optional;

/** Authenticates users against an external identity provider. */
@JahazielSpi(version = 1)
public interface IdentityProvider {
  /**
   * Authenticates a token and returns the user identity.
   *
   * @param token the token to authenticate
   * @return the authenticated identity
   */
  UserIdentity authenticate(String token);

  /**
   * Finds a user by external identifier.
   *
   * @param externalId the subject claim from the identity provider
   * @return the identity, empty when unknown
   */
  Optional<UserIdentity> findById(String externalId);

  /**
   * Creates an identity from raw claims.
   *
   * @param claims the verified claims
   * @return the created identity
   */
  UserIdentity createFromClaims(Map<String, Object> claims);
}
