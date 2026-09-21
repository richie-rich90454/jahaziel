/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link Node}. */
class NodeTest {
  private Node node;

  @BeforeEach
  void setUp() {
    node =
        new Node(
            new NodeId("node-a"),
            "Primary node",
            "default",
            new NodeCapacity(14, 49152, 500),
            "sha256:abc");
  }

  @Test
  void startsHealthyAndEmpty() {
    assertInstanceOf(NodeState.Healthy.class, node.state());
    assertTrue(node.workspaces().isEmpty());
    assertEquals(NodeMetrics.zero(), node.metrics());
    assertEquals("Primary node", node.name());
    assertEquals("default", node.region());
    assertEquals("sha256:abc", node.certFingerprint());
    assertTrue(node.lastHeartbeat().isEmpty());
  }

  @Test
  void assignsAndReleases() {
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    node.assign(workspace);
    assertEquals(Set.of(workspace), node.workspaces());
    node.release(workspace);
    assertTrue(node.workspaces().isEmpty());
  }

  @Test
  void duplicateAssignAndUnknownReleaseThrow() {
    WorkspaceId workspace = new WorkspaceId(UUID.randomUUID());
    node.assign(workspace);
    assertThrows(IllegalArgumentException.class, () -> node.assign(workspace));
    assertThrows(
        IllegalArgumentException.class, () -> node.release(new WorkspaceId(UUID.randomUUID())));
  }

  @Test
  void drainingRefusesPlacements() {
    node.drain();
    assertInstanceOf(NodeState.Draining.class, node.state());
    assertThrows(
        IllegalStateException.class, () -> node.assign(new WorkspaceId(UUID.randomUUID())));
  }

  @Test
  void degradeRecoverCycle() {
    node.degrade("disk slow");
    NodeState.Degraded degraded = assertInstanceOf(NodeState.Degraded.class, node.state());
    assertEquals("disk slow", degraded.reason());
    node.recover();
    assertInstanceOf(NodeState.Healthy.class, node.state());
  }

  @Test
  void recoverRequiresDegraded() {
    assertThrows(IllegalStateException.class, node::recover);
  }

  @Test
  void blankRegistrationFieldsAreRejected() {
    NodeCapacity capacity = new NodeCapacity(14, 49152, 500);
    assertThrows(
        IllegalArgumentException.class,
        () -> new Node(new NodeId("n"), "  ", "default", capacity, "fp"));
    assertThrows(
        IllegalArgumentException.class, () -> new Node(new NodeId("n"), "n", "  ", capacity, "fp"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Node(new NodeId("n"), "n", "default", capacity, "  "));
  }

  @Test
  void blankReasonsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> node.degrade("  "));
    assertThrows(IllegalArgumentException.class, () -> node.markUnhealthy("  "));
  }

  @Test
  void unhealthyAndHeartbeat() {
    node.markUnhealthy("no heartbeat");
    NodeState.Unhealthy unhealthy = assertInstanceOf(NodeState.Unhealthy.class, node.state());
    assertEquals("no heartbeat", unhealthy.reason());
    assertThrows(IllegalStateException.class, () -> node.markUnhealthy("again"));
    assertThrows(IllegalStateException.class, node::drain);
    node.recordHeartbeat();
    assertTrue(node.lastHeartbeat().isPresent());
  }

  @Test
  void metricsUpdate() {
    NodeMetrics sample = new NodeMetrics(41, 1024, 2048, 1.5);
    node.updateMetrics(sample);
    assertEquals(sample, node.metrics());
  }
}
