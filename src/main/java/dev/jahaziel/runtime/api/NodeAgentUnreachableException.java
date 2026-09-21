/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.runtime.api;

/** Thrown when the node agent cannot be reached. Starts fail safe, never fail open. */
public final class NodeAgentUnreachableException extends RuntimeException {
  /**
   * Creates the exception.
   *
   * @param message why the agent is unreachable
   */
  public NodeAgentUnreachableException(String message) {
    super(message);
  }

  /**
   * Creates the exception with a cause.
   *
   * @param message why the agent is unreachable
   * @param cause the underlying failure
   */
  public NodeAgentUnreachableException(String message, Throwable cause) {
    super(message, cause);
  }
}
