/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.node.domain.Node;
import dev.jahaziel.node.domain.NodeCapacity;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.node.domain.NodeMetrics;
import dev.jahaziel.node.domain.NodeState;
import dev.jahaziel.node.persistence.NodeEntity;
import org.junit.jupiter.api.Test;

/** Behavior of {@link NodeMapper} with stubbed rows. */
class NodeMapperTest {
  private final NodeId nodeId = new NodeId("node-a");
  private final NodeCapacity capacity = new NodeCapacity(14, 49152, 500);

  @Test
  void healthyStaysHealthyWithCapacity() {
    Node node = NodeMapper.toDomain(row("healthy"));
    assertInstanceOf(NodeState.Healthy.class, node.state());
    assertEquals(nodeId, node.id());
    assertEquals(capacity, node.capacity());
    assertEquals(NodeMetrics.zero(), node.metrics());
    NodeEntity saved = NodeMapper.toEntity(node);
    assertEquals("node-a", saved.getNodeId());
    assertEquals("healthy", saved.getState());
    assertEquals(14, saved.getCpuCores());
  }

  @Test
  void degradedRestoresWithNotRecordedReason() {
    Node node = NodeMapper.toDomain(row("degraded"));
    NodeState.Degraded degraded = assertInstanceOf(NodeState.Degraded.class, node.state());
    assertEquals("not recorded", degraded.reason());
  }

  @Test
  void unknownStateThrows() {
    assertThrows(IllegalStateException.class, () -> NodeMapper.toDomain(row("bogus")));
  }

  private NodeEntity row(String state) {
    NodeEntity row = mock(NodeEntity.class);
    when(row.getNodeId()).thenReturn("node-a");
    when(row.getName()).thenReturn("Primary node");
    when(row.getRegion()).thenReturn("default");
    when(row.getState()).thenReturn(state);
    when(row.getCpuCores()).thenReturn(14);
    when(row.getMemoryMib()).thenReturn(49152L);
    when(row.getDiskGib()).thenReturn(500L);
    when(row.getCertFingerprint()).thenReturn("sha256:abc");
    return row;
  }
}
