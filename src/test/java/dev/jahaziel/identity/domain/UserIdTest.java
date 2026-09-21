/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link UserId}. */
class UserIdTest {
  @Test
  void equalValuesAreEqual() {
    UUID value = UUID.randomUUID();
    assertEquals(new UserId(value), new UserId(value));
  }

  @Test
  void differentValuesAreNotEqual() {
    assertNotEquals(new UserId(UUID.randomUUID()), new UserId(UUID.randomUUID()));
  }

  @Test
  void nullValueIsRejected() {
    assertThrows(NullPointerException.class, () -> new UserId(null));
  }
}
