/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspacePortCoverageGapTest {
  @Test
  void lastSeenRoundTrip() {
    WorkspacePortEntity entity = new WorkspacePortEntity();
    entity.setId(new WorkspacePortEntity.Id(UUID.randomUUID(), 8080));
    Instant seen = Instant.parse("2026-05-06T07:08:09Z");
    entity.setLastSeenAt(seen);
    assertEquals(seen, entity.getLastSeenAt());
    entity.setLastSeenAt(null);
    assertEquals(null, entity.getLastSeenAt());
  }
}
