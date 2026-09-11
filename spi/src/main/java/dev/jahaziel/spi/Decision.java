/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Policy decision for an extension request. */
public enum Decision {
  /** The extension may be installed. */
  ALLOW,
  /** The extension must not be installed. */
  BLOCK,
  /** Installation waits for an administrator approval. */
  REQUIRE_APPROVAL
}
