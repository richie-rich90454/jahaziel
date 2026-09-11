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
    node = new Node(new NodeId("node-a"), new NodeCapacity(14, 49152, 500));
  }

  @Test
  void startsHealthyAndEmpty() {
    assertInstanceOf(NodeState.Healthy.class, node.state());
    assertTrue(node.workspaces().isEmpty());
    assertEquals(NodeMetrics.zero(), node.metrics());
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
  void blankReasonsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> node.degrade("  "));
  }

  @Test
  void metricsUpdate() {
    NodeMetrics sample = new NodeMetrics(41, 1024, 2048, 1.5);
    node.updateMetrics(sample);
    assertEquals(sample, node.metrics());
  }
}
