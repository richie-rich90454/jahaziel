/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.node.api.NodeService;
import dev.jahaziel.node.domain.Node;
import dev.jahaziel.node.domain.NodeCapacity;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.node.domain.NodeState;
import dev.jahaziel.node.persistence.NodeEntity;
import dev.jahaziel.node.persistence.NodeRepository;
import dev.jahaziel.spi.JobContext;
import dev.jahaziel.spi.JobScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NodeServiceCoverageGapTest {
  private NodeEntity healthyRow(String id) {
    NodeEntity row = mock(NodeEntity.class);
    when(row.getNodeId()).thenReturn(id);
    when(row.getName()).thenReturn("Primary node");
    when(row.getRegion()).thenReturn("default");
    when(row.getState()).thenReturn("healthy");
    when(row.getCpuCores()).thenReturn(4);
    when(row.getMemoryMib()).thenReturn(8192L);
    when(row.getDiskGib()).thenReturn(100L);
    when(row.getCertFingerprint()).thenReturn("fp");
    when(row.getRegisteredAt()).thenReturn(Instant.parse("2026-09-01T00:00:00Z"));
    when(row.getLastHeartbeat()).thenReturn(Instant.parse("2026-09-02T00:00:00Z"));
    return row;
  }

  @Test
  void heartbeatPersists() {
    NodeRepository nodes = mock(NodeRepository.class);
    NodeServiceImpl service = new NodeServiceImpl(nodes);
    NodeId id = new NodeId("node-a");
    NodeEntity row = healthyRow("node-a");
    when(nodes.findById(id.value())).thenReturn(Optional.of(row));
    service.heartbeat(id);
    ArgumentCaptor<NodeEntity> captor = ArgumentCaptor.forClass(NodeEntity.class);
    verify(nodes).save(captor.capture());
    assertEquals("node-a", captor.getValue().getNodeId());
  }

  @Test
  void markUnhealthyPersists() {
    NodeRepository nodes = mock(NodeRepository.class);
    NodeServiceImpl service = new NodeServiceImpl(nodes);
    NodeId id = new NodeId("node-a");
    NodeEntity row = healthyRow("node-a");
    when(nodes.findById(id.value())).thenReturn(Optional.of(row));
    service.markUnhealthy(id, "gone");
    ArgumentCaptor<NodeEntity> captor = ArgumentCaptor.forClass(NodeEntity.class);
    verify(nodes).save(captor.capture());
    assertEquals("unhealthy", captor.getValue().getState());
  }

  @Test
  void mapperDrainingUnhealthyAndNullRegisteredAt() {
    NodeEntity draining = healthyRow("node-a");
    when(draining.getState()).thenReturn("draining");
    assertInstanceOf(NodeState.Draining.class, NodeMapper.toDomain(draining).state());
    NodeEntity unhealthy = healthyRow("node-a");
    when(unhealthy.getState()).thenReturn("unhealthy");
    assertInstanceOf(NodeState.Unhealthy.class, NodeMapper.toDomain(unhealthy).state());
    NodeEntity noreg = healthyRow("node-a");
    when(noreg.getRegisteredAt()).thenReturn(null);
    assertEquals("node-a", NodeMapper.toDomain(noreg).id().value());
    Node degraded =
        new Node(
            new NodeId("node-a"), "Primary node", "default", new NodeCapacity(4, 8192, 100), "fp");
    degraded.degrade("slow");
    assertEquals("degraded", NodeMapper.toEntity(degraded).getState());
    Node drainNode = fresh();
    drainNode.drain();
    assertEquals("draining", NodeMapper.toEntity(drainNode).getState());
    Node lost = fresh();
    lost.markUnhealthy("gone");
    assertEquals("unhealthy", NodeMapper.toEntity(lost).getState());
    assertThrows(IllegalArgumentException.class, () -> NodeMapper.stateToString(null));
  }

  @Test
  void healthCheckBranches() {
    NodeService nodes = mock(NodeService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC);
    HealthCheckJob job = new HealthCheckJob(nodes, Duration.ofSeconds(60), clock);
    Node draining = fresh();
    draining.drain();
    Node freshNode = fresh();
    freshNode.recordHeartbeat();
    Node stale = fresh();
    Node oldBeat =
        new Node(
            new NodeId(UUID.randomUUID().toString().substring(0, 8)),
            "Primary node",
            "default",
            new NodeCapacity(4, 8192, 100),
            "fp",
            Instant.parse("2026-09-01T00:00:00Z"),
            Instant.parse("2026-09-01T00:00:00Z"));
    Node already = fresh();
    already.markUnhealthy("gone");
    when(nodes.list()).thenReturn(List.of(draining, freshNode, stale, oldBeat, already));
    org.mockito.Mockito.doThrow(new IllegalStateException("already"))
        .when(nodes)
        .markUnhealthy(already.id(), "no heartbeat for 60s");
    job.run(JobContext.now());
    verify(nodes).markUnhealthy(stale.id(), "no heartbeat for 60s");
    verify(nodes).markUnhealthy(oldBeat.id(), "no heartbeat for 60s");
    assertEquals(Duration.ofSeconds(30), job.period());
    assertEquals(JobScope.CLUSTER, job.scope());
  }

  private Node fresh() {
    return new Node(
        new NodeId(UUID.randomUUID().toString().substring(0, 8)),
        "Primary node",
        "default",
        new NodeCapacity(4, 8192, 100),
        "fp");
  }
}
