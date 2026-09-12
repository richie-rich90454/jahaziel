/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.api;

import dev.jahaziel.snapshot.domain.SnapshotKind;

/**
 * Stored snapshot kind forms. The database uses dashes where the enum uses underscores.
 */
public final class SnapshotKinds {
  private SnapshotKinds() {}

  /**
   * Maps a kind to its stored form.
   *
   * @param kind the kind
   * @return the lowercase stored form
   */
  public static String toString(SnapshotKind kind) {
    return switch (kind) {
      case AUTO -> "auto";
      case MANUAL -> "manual";
      case PRE_DELETE -> "pre-delete";
    };
  }

  /**
   * Parses a stored kind.
   *
   * @param kind the stored form
   * @return the kind
   */
  public static SnapshotKind fromString(String kind) {
    return switch (kind) {
      case "auto" -> SnapshotKind.AUTO;
      case "manual" -> SnapshotKind.MANUAL;
      case "pre-delete" -> SnapshotKind.PRE_DELETE;
      default -> throw new IllegalStateException("unknown snapshot kind " + kind);
    };
  }
}
