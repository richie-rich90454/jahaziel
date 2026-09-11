/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.api;

import dev.jahaziel.node.domain.Node;
import dev.jahaziel.node.domain.NodeCapacity;
import dev.jahaziel.node.domain.NodeId;
import java.util.List;

/** Nodes, looked up by the rest of the platform. */
public interface NodeService {
  /**
   * Registers a healthy node.
   *
   * @param id the node identifier
   * @param name the human-readable name
   * @param region the hosting region
   * @param capacity the total capacity
   * @param certFingerprint the agent certificate fingerprint
   * @return the registered node
   * @throws IllegalArgumentException when the node is already registered
   */
  Node register(
      NodeId id, String name, String region, NodeCapacity capacity, String certFingerprint);

  /**
   * Returns one node.
   *
   * @param id the node identifier
   * @return the node
   * @throws NodeNotFoundException when the node does not exist
   */
  Node get(NodeId id);

  /**
   * Returns all nodes.
   *
   * @return the nodes, empty when none exist
   */
  List<Node> list();

  /**
   * Starts draining a node. Placed workspaces stay; no new placements.
   *
   * @param id the node identifier
   * @throws NodeNotFoundException when the node does not exist
   */
  void drain(NodeId id);

  /**
   * Records a heartbeat.
   *
   * @param id the node identifier
   * @throws NodeNotFoundException when the node does not exist
   */
  void heartbeat(NodeId id);

  /**
   * Marks the node unhealthy, for example on missed heartbeats.
   *
   * @param id the node identifier
   * @param reason why the node is considered lost
   * @throws NodeNotFoundException when the node does not exist
   */
  void markUnhealthy(NodeId id, String reason);
}
