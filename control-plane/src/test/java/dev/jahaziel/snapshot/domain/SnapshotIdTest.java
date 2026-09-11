/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link SnapshotId}. */
class SnapshotIdTest {
  @Test
  void equalValuesAreEqual() {
    UUID value = UUID.randomUUID();
    assertEquals(new SnapshotId(value), new SnapshotId(value));
  }

  @Test
  void differentValuesAreNotEqual() {
    assertNotEquals(new SnapshotId(UUID.randomUUID()), new SnapshotId(UUID.randomUUID()));
  }

  @Test
  void nullValueIsRejected() {
    assertThrows(NullPointerException.class, () -> new SnapshotId(null));
  }
}
