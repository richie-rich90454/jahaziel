/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.auth;

/** Thrown when a session token is rejected. Carries a safe reason, never the token. */
public final class JwtRejectedException extends RuntimeException {
  /**
   * Creates the exception.
   *
   * @param reason why the token was rejected
   */
  public JwtRejectedException(String reason) {
    super(reason);
  }

  /**
   * Creates the exception with a cause.
   *
   * @param reason why the token was rejected
   * @param cause the underlying failure
   */
  public JwtRejectedException(String reason, Throwable cause) {
    super(reason, cause);
  }
}
