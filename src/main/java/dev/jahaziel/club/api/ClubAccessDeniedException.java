/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.club.api;

/** Thrown when a club role or ownership check fails. */
public class ClubAccessDeniedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message the reason, safe to show to the caller
   */
  public ClubAccessDeniedException(String message) {
    super(message);
  }
}
