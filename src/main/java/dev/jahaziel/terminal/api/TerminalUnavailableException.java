/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.api;

/** The host cannot start an interactive terminal process. */
public final class TerminalUnavailableException extends RuntimeException {
  public TerminalUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public TerminalUnavailableException(String message) {
    super(message);
  }
}
