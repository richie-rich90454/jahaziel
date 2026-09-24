/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Coverage of {@link NodeEntity}. */
class NodeEntityTest {
  @Test
  void storesAllFields() {
    NodeEntity entity = new NodeEntity();
    entity.setNodeId("node-a");
    entity.setName("Primary");
    entity.setRegion("default");
    entity.setState("HEALTHY");
    entity.setCpuCores(14);
    entity.setMemoryMib(49152L);
    entity.setDiskGib(500L);
    entity.setRegisteredAt(Instant.EPOCH);
    entity.setLastHeartbeat(Instant.now());
    entity.setCertFingerprint("sha256:abc");
    assertEquals("node-a", entity.getNodeId());
    assertEquals("Primary", entity.getName());
    assertEquals("default", entity.getRegion());
    assertEquals("HEALTHY", entity.getState());
    assertEquals(14, entity.getCpuCores());
    assertEquals(49152L, entity.getMemoryMib());
    assertEquals(500L, entity.getDiskGib());
    assertEquals(Instant.EPOCH, entity.getRegisteredAt());
    assertEquals("sha256:abc", entity.getCertFingerprint());
  }

  @Test
  void equalsByNodeId() {
    NodeEntity first = new NodeEntity();
    first.setNodeId("node-a");
    first.setName("a");
    NodeEntity second = new NodeEntity();
    second.setNodeId("node-a");
    second.setName("b");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setNodeId("node-b");
    assertFalse(first.equals(second));
  }
}
