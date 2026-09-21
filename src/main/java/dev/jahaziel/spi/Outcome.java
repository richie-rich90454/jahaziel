/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

/** Outcome of an audited action. */
public enum Outcome {
  /** The action completed. */
  SUCCESS,
  /** The action failed. */
  FAILURE,
  /** The action was refused by policy. */
  DENIED
}
