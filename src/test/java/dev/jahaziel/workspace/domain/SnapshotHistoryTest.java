/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.snapshot.domain.SnapshotId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link SnapshotHistory}. */
class SnapshotHistoryTest {
  @Test
  void startsEmpty() {
    assertTrue(new SnapshotHistory().snapshots().isEmpty());
  }

  @Test
  void recordsInOrder() {
    SnapshotHistory history = new SnapshotHistory();
    SnapshotId first = new SnapshotId(UUID.randomUUID());
    SnapshotId second = new SnapshotId(UUID.randomUUID());
    history.record(first);
    history.record(second);
    assertEquals(List.of(first, second), history.snapshots());
  }

  @Test
  void nullSnapshotIsRejected() {
    assertThrows(NullPointerException.class, () -> new SnapshotHistory().record(null));
  }
}
