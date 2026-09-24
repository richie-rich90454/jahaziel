/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Null guards of the node state records. */
class NodeRecordTest {
  @Test
  void degradedGuardsInputs() {
    Instant at = Instant.now();
    NodeState.Degraded degraded = new NodeState.Degraded(at, "disk slow");
    assertEquals("disk slow", degraded.reason());
    assertEquals(at, degraded.at());
    assertThrows(NullPointerException.class, () -> new NodeState.Degraded(null, "r"));
    assertThrows(IllegalArgumentException.class, () -> new NodeState.Degraded(at, null));
    assertThrows(IllegalArgumentException.class, () -> new NodeState.Degraded(at, "  "));
  }

  @Test
  void unhealthyGuardsInputs() {
    Instant at = Instant.now();
    NodeState.Unhealthy unhealthy = new NodeState.Unhealthy(at, "no heartbeat");
    assertEquals("no heartbeat", unhealthy.reason());
    assertEquals(at, unhealthy.at());
    assertThrows(NullPointerException.class, () -> new NodeState.Unhealthy(null, "r"));
    assertThrows(IllegalArgumentException.class, () -> new NodeState.Unhealthy(at, null));
    assertThrows(IllegalArgumentException.class, () -> new NodeState.Unhealthy(at, "  "));
  }
}
