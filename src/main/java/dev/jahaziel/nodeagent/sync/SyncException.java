/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.sync;

/** Thrown when the sync protocol is violated or persistence fails. */
public final class SyncException extends RuntimeException {
  /**
   * Creates the exception.
   *
   * @param message what went wrong
   */
  public SyncException(String message) {
    super(message);
  }

  /**
   * Creates the exception with a cause.
   *
   * @param message what went wrong
   * @param cause the underlying failure
   */
  public SyncException(String message, Throwable cause) {
    super(message, cause);
  }
}
