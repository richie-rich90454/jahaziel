/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.internal;

/** No more interactive language-server sessions are available. */
public final class LanguageSessionLimitException extends RuntimeException {
  public LanguageSessionLimitException() {
    super("language-service session limit reached");
  }
}
