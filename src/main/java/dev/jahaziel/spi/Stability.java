/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Stability of a plugin interface. */
public enum Stability {
  /** Stable. Breaking changes arrive as a new interface version. */
  STABLE,
  /** Beta. Usable but still allowed to change between minor releases. */
  BETA,
  /** Experimental. May change or disappear at any time. */
  EXPERIMENTAL
}
