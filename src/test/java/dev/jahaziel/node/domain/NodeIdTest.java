/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Behavior of {@link NodeId}. */
class NodeIdTest {
  @Test
  void equalValuesAreEqual() {
    assertEquals(new NodeId("node-a"), new NodeId("node-a"));
  }

  @Test
  void differentValuesAreNotEqual() {
    assertNotEquals(new NodeId("node-a"), new NodeId("node-b"));
  }

  @Test
  void blankValuesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new NodeId("  "));
    assertThrows(NullPointerException.class, () -> new NodeId(null));
  }
}
