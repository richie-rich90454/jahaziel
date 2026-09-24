/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Final gap tests: every operand of the tag guard. */
class SnapshotTagFinalTest {
  @Test
  void rejectsEachBadShape() {
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag(null));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag("  "));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag("a..b"));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag("a/b"));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag("a\\b"));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag(".hidden"));
    assertThrows(IllegalArgumentException.class, () -> new SnapshotTag("/abs"));
  }

  @Test
  void acceptsPlainTag() {
    assertEquals("v1", new SnapshotTag("v1").value());
  }
}
