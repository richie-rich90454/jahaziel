/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

import dev.jahaziel.identity.domain.UserId;

/** Thrown when a session token is rejected. Carries a safe reason, never the token. */
public final class JwtRejectedException extends RuntimeException {
  private final UserId userId;

  /**
   * Creates the exception.
   *
   * @param reason why the token was rejected
   */
  public JwtRejectedException(String reason) {
    super(reason);
    this.userId = null;
  }

  /**
   * Creates the exception with a cause.
   *
   * @param reason why the token was rejected
   * @param cause the underlying failure
   */
  public JwtRejectedException(String reason, Throwable cause) {
    super(reason, cause);
    this.userId = null;
  }

  /**
   * Creates the exception for a user.
   *
   * @param user the acting user, for context
   * @param reason why the token was rejected
   */
  public JwtRejectedException(UserId user, String reason) {
    super(reason + ": " + user);
    this.userId = user;
  }

  /** Returns the acting user, or null for malformed tokens. */
  public UserId userId() {
    return userId;
  }
}
