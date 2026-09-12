/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.internal;

import dev.jahaziel.node.domain.Node;
import dev.jahaziel.node.domain.NodeCapacity;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.node.domain.NodeState;
import dev.jahaziel.node.persistence.NodeEntity;

/**
 * Maps node rows to domain objects and back. Metrics are live-only and never persisted; placements
 * live in the machine rows, so restored nodes start with an empty set.
 */
public final class NodeMapper {
  private NodeMapper() {}

  /**
   * Maps a node to its row.
   *
   * @param node the node
   * @return the row
   */
  public static NodeEntity toEntity(Node node) {
    NodeEntity row = new NodeEntity();
    row.setNodeId(node.id().value());
    row.setName(node.name());
    row.setRegion(node.region());
    row.setState(stateToString(node.state()));
    row.setCpuCores(node.capacity().cpuCores());
    row.setMemoryMib(node.capacity().memoryMib());
    row.setDiskGib(node.capacity().diskGib());
    row.setRegisteredAt(node.registeredAt());
    row.setLastHeartbeat(node.lastHeartbeat().orElse(null));
    row.setCertFingerprint(node.certFingerprint());
    return row;
  }

  /**
   * Rebuilds a node from its row. Stored degradation reasons are not recorded, so restored degraded
   * and unhealthy states record that the reason was not recorded.
   *
   * @param row the node row
   * @return the node
   */
  public static Node toDomain(NodeEntity row) {
    Node node =
        new Node(
            new NodeId(row.getNodeId()),
            row.getName(),
            row.getRegion(),
            new NodeCapacity(row.getCpuCores(), row.getMemoryMib(), row.getDiskGib()),
            row.getCertFingerprint());
    replay(node, row.getState());
    if (row.getLastHeartbeat() != null) {
      node.recordHeartbeat();
    }
    return node;
  }

  /**
   * Maps a state to its stored form.
   *
   * @param state the state
   * @return the lowercase stored form
   */
  public static String stateToString(NodeState state) {
    if (state instanceof NodeState.Healthy) {
      return "healthy";
    }
    if (state instanceof NodeState.Degraded) {
      return "degraded";
    }
    if (state instanceof NodeState.Draining) {
      return "draining";
    }
    if (state instanceof NodeState.Unhealthy) {
      return "unhealthy";
    }
    throw new IllegalArgumentException("Unknown state " + state);
  }

  private static void replay(Node node, String state) {
    switch (state) {
      case "healthy" -> {}
      case "degraded" -> node.degrade("not recorded");
      case "draining" -> node.drain();
      case "unhealthy" -> node.markUnhealthy("not recorded");
      default ->
          throw new IllegalStateException("Node " + node.id() + " has unknown state " + state);
    }
  }
}
