/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import dev.jahaziel.identity.domain.UserId;

/** Thrown when a user does not exist. */
public final class UserNotFoundException extends RuntimeException {
  private final UserId userId;

  /**
   * Creates the exception.
   *
   * @param userId the missing user identifier
   */
  public UserNotFoundException(UserId userId) {
    super("User " + userId + " not found");
    this.userId = userId;
  }

  /** Returns the missing user identifier. */
  public UserId userId() {
    return userId;
  }
}
