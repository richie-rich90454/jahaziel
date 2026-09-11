/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link WorkspaceId}. */
class WorkspaceIdTest {
  @Test
  void equalValuesAreEqual() {
    UUID value = UUID.randomUUID();
    assertEquals(new WorkspaceId(value), new WorkspaceId(value));
  }

  @Test
  void differentValuesAreNotEqual() {
    assertNotEquals(new WorkspaceId(UUID.randomUUID()), new WorkspaceId(UUID.randomUUID()));
  }

  @Test
  void nullValueIsRejected() {
    assertThrows(NullPointerException.class, () -> new WorkspaceId(null));
  }
}
