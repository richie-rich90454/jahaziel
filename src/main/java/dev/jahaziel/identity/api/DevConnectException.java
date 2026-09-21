/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.api;

/**
 * A DevConnect step failed: discovery, token exchange, ID token verification, or UserInfo. The
 * message names the target and the cause so an operator can diagnose without a stack trace.
 */
public class DevConnectException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates the failure.
   *
   * @param message the diagnosis
   */
  public DevConnectException(String message) {
    super(message);
  }

  /**
   * Creates the failure with a cause.
   *
   * @param message the diagnosis
   * @param cause the underlying failure
   */
  public DevConnectException(String message, Throwable cause) {
    super(message, cause);
  }
}
