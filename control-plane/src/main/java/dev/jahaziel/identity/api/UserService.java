/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.spi.UserIdentity;

/** Users, looked up by the rest of the platform. */
public interface UserService {
  /**
   * Returns one user.
   *
   * @param id the user identifier
   * @return the identity with roles and groups
   * @throws UserNotFoundException when the user does not exist
   */
  UserIdentity get(UserId id);

  /**
   * Checks existence.
   *
   * @param id the user identifier
   * @return true when the user exists
   */
  boolean exists(UserId id);
}
