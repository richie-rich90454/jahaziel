/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

/** A selected language server could not be started safely. */
public final class LanguageServerStartException extends RuntimeException {
  public LanguageServerStartException(String message, Throwable cause) {
    super(message, cause);
  }
}
