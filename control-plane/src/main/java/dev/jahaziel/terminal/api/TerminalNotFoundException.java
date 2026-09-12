/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.api;

import dev.jahaziel.terminal.domain.TerminalSessionId;

/** Thrown when a terminal session does not exist. */
public final class TerminalNotFoundException extends RuntimeException {
  private final TerminalSessionId sessionId;

  /**
   * Creates the exception.
   *
   * @param sessionId the missing session identifier
   */
  public TerminalNotFoundException(TerminalSessionId sessionId) {
    super("Terminal session " + sessionId + " not found");
    this.sessionId = sessionId;
  }

  /** Returns the missing session identifier. */
  public TerminalSessionId sessionId() {
    return sessionId;
  }
}
