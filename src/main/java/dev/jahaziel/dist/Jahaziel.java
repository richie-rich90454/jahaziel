/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

/**
 * Executable-jar launcher. Holds the only {@code System.exit} in the codebase so the dispatch logic
 * in {@link JahazielMain} stays unit-testable; excluded from coverage for that reason.
 */
public final class Jahaziel {
  private Jahaziel() {}

  /**
   * Starts Jahaziel.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    System.exit(JahazielMain.run(args, System.out, System.err));
  }
}
