/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.api;

/** A configured terminal session limit was reached. */
public final class TerminalLimitException extends RuntimeException {
  public TerminalLimitException(String message) {
    super(message);
  }
}
