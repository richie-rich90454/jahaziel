/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.node.api.NodeNotFoundException;
import dev.jahaziel.node.domain.Node;
import dev.jahaziel.node.domain.NodeCapacity;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.node.domain.NodeState;
import dev.jahaziel.node.persistence.NodeEntity;
import dev.jahaziel.node.persistence.NodeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Behavior of {@link NodeServiceImpl} with stubbed rows. */
class NodeServiceImplTest {
  private NodeRepository nodes;
  private NodeServiceImpl service;
  private NodeId nodeId;
  private NodeCapacity capacity;

  @BeforeEach
  void setUp() {
    nodes = mock(NodeRepository.class);
    service = new NodeServiceImpl(nodes);
    nodeId = new NodeId("node-a");
    capacity = new NodeCapacity(14, 49152, 500);
  }

  @Test
  void registerPersistsHealthyRow() {
    when(nodes.existsById(nodeId.value())).thenReturn(false);
    Node node = service.register(nodeId, "Primary node", "default", capacity, "sha256:abc");
    assertInstanceOf(NodeState.Healthy.class, node.state());
    ArgumentCaptor<NodeEntity> captor = ArgumentCaptor.forClass(NodeEntity.class);
    verify(nodes).save(captor.capture());
    assertEquals("node-a", captor.getValue().getNodeId());
    assertEquals("healthy", captor.getValue().getState());
    assertEquals(14, captor.getValue().getCpuCores());
  }

  @Test
  void duplicateRegisterThrows() {
    when(nodes.existsById(nodeId.value())).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.register(nodeId, "Primary node", "default", capacity, "sha256:abc"));
    verify(nodes, never()).save(any());
  }

  @Test
  void getMissingThrowsWithId() {
    when(nodes.findById(nodeId.value())).thenReturn(Optional.empty());
    NodeNotFoundException failure =
        assertThrows(NodeNotFoundException.class, () -> service.get(nodeId));
    assertEquals(nodeId, failure.nodeId());
  }

  @Test
  void drainFlipsStoredStateToDraining() {
    NodeEntity healthy = healthyRow();
    when(nodes.findById(nodeId.value())).thenReturn(Optional.of(healthy));
    service.drain(nodeId);
    ArgumentCaptor<NodeEntity> captor = ArgumentCaptor.forClass(NodeEntity.class);
    verify(nodes).save(captor.capture());
    assertEquals("node-a", captor.getValue().getNodeId());
    assertEquals("draining", captor.getValue().getState());
  }

  @Test
  void listMapsRows() {
    NodeEntity second = mock(NodeEntity.class);
    when(second.getNodeId()).thenReturn("node-b");
    when(second.getName()).thenReturn("Second node");
    when(second.getRegion()).thenReturn("default");
    when(second.getState()).thenReturn("healthy");
    when(second.getCpuCores()).thenReturn(8);
    when(second.getMemoryMib()).thenReturn(32768L);
    when(second.getDiskGib()).thenReturn(250L);
    when(second.getCertFingerprint()).thenReturn("sha256:def");
    NodeEntity healthy = healthyRow();
    when(nodes.findAll()).thenReturn(List.of(healthy, second));
    List<Node> found = service.list();
    assertEquals(2, found.size());
    assertEquals(new NodeId("node-a"), found.get(0).id());
    assertEquals(new NodeId("node-b"), found.get(1).id());
  }

  private NodeEntity healthyRow() {
    NodeEntity row = mock(NodeEntity.class);
    when(row.getNodeId()).thenReturn("node-a");
    when(row.getName()).thenReturn("Primary node");
    when(row.getRegion()).thenReturn("default");
    when(row.getState()).thenReturn("healthy");
    when(row.getCpuCores()).thenReturn(14);
    when(row.getMemoryMib()).thenReturn(49152L);
    when(row.getDiskGib()).thenReturn(500L);
    when(row.getCertFingerprint()).thenReturn("sha256:abc");
    return row;
  }
}
