/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.language.api;

/** Runtime requirements shared by preflight diagnostics and language-pack execution. */
public final class LanguageRuntimeRequirements {
  private LanguageRuntimeRequirements() {}

  /**
   * Returns whether the managed language service identified by {@code languageId} needs Node.js.
   */
  public static boolean usesNodeRuntime(String languageId) {
    return "typescript".equals(languageId) || "python".equals(languageId);
  }
}
