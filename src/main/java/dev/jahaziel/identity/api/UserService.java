/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.spi.UserIdentity;
import java.util.List;

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

  /**
   * Checks whether the account is active, the per-request kill switch. A missing account is not
   * active.
   *
   * @param id the user identifier
   * @return true when the account exists and its status is active
   */
  boolean isActive(UserId id);

  /**
   * Checks MFA enrollment.
   *
   * @param id the user identifier
   * @return true when MFA enrollment is completed
   * @throws UserNotFoundException when the user does not exist
   */
  boolean isMfaEnabled(UserId id);

  /**
   * Changes the account status.
   *
   * @param id the user identifier
   * @param status one of active, suspended, revoked
   * @throws UserNotFoundException when the user does not exist
   */
  void updateStatus(UserId id, String status);

  /**
   * Lists all users with full read models.
   *
   * @return every user
   */
  List<AppUser> listUsers();

  /**
   * Returns the URL-safe slug of a user. Initial rule: the lowercase email local part.
   *
   * @param id the user identifier
   * @return the slug
   * @throws UserNotFoundException when the user does not exist
   */
  String slug(UserId id);
}
