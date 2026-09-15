/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

/**
 * The identity provider authenticated a user we must not admit: a disabled account, or an email
 * already linked to a different subject. Answer with 403; an operator resolves it.
 */
public class LoginRejectedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates the rejection.
   *
   * @param message the operator-facing reason
   */
  public LoginRejectedException(String message) {
    super(message);
  }
}
