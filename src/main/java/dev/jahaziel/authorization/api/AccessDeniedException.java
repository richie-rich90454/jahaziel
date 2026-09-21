/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.authorization.api;

import dev.jahaziel.identity.domain.UserId;

/** Thrown when a user lacks permission. Carries who and what for the error body. */
public final class AccessDeniedException extends RuntimeException {
  private final UserId userId;
  private final String action;

  /**
   * Creates the exception.
   *
   * @param userId the acting user
   * @param action the refused action
   */
  public AccessDeniedException(UserId userId, String action) {
    super("Forbidden");
    this.userId = userId;
    this.action = action;
  }

  /** Returns the acting user. */
  public UserId userId() {
    return userId;
  }

  /** Returns the refused action. */
  public String action() {
    return action;
  }
}
