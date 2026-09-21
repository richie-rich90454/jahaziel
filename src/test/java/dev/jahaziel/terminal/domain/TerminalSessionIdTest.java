/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.terminal.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link TerminalSessionId}. */
class TerminalSessionIdTest {
  @Test
  void equalValuesAreEqual() {
    UUID value = UUID.randomUUID();
    assertEquals(new TerminalSessionId(value), new TerminalSessionId(value));
  }

  @Test
  void differentValuesAreNotEqual() {
    assertNotEquals(
        new TerminalSessionId(UUID.randomUUID()), new TerminalSessionId(UUID.randomUUID()));
  }

  @Test
  void nullValueIsRejected() {
    assertThrows(NullPointerException.class, () -> new TerminalSessionId(null));
  }
}
