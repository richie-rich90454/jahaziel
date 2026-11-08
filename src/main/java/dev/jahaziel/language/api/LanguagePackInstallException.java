/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.api;

/** A pinned language-pack download or verification failed. */
public final class LanguagePackInstallException extends RuntimeException {
  public LanguagePackInstallException(String message, Throwable cause) {
    super(message, cause);
  }
}
