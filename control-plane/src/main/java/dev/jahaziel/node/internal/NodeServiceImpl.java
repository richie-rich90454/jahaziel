/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.internal;

import dev.jahaziel.node.api.NodeNotFoundException;
import dev.jahaziel.node.api.NodeService;
import dev.jahaziel.node.domain.Node;
import dev.jahaziel.node.domain.NodeCapacity;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.node.persistence.NodeEntity;
import dev.jahaziel.node.persistence.NodeRepository;
import java.util.List;
import java.util.Objects;

/** Database-backed nodes. */
public final class NodeServiceImpl implements NodeService {
  private final NodeRepository nodes;

  /**
   * Creates the service.
   *
   * @param nodes the node rows
   */
  public NodeServiceImpl(NodeRepository nodes) {
    this.nodes = Objects.requireNonNull(nodes, "nodes");
  }

  @Override
  public Node register(
      NodeId id, String name, String region, NodeCapacity capacity, String certFingerprint) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(capacity, "capacity");
    if (nodes.existsById(id.value())) {
      throw new IllegalArgumentException("Node " + id + " already registered");
    }
    Node node = new Node(id, name, region, capacity, certFingerprint);
    nodes.save(NodeMapper.toEntity(node));
    return node;
  }

  @Override
  public Node get(NodeId id) {
    Objects.requireNonNull(id, "id");
    NodeEntity row = nodes.findById(id.value()).orElseThrow(() -> new NodeNotFoundException(id));
    return NodeMapper.toDomain(row);
  }

  @Override
  public List<Node> list() {
    return nodes.findAll().stream().map(NodeMapper::toDomain).toList();
  }

  @Override
  public void drain(NodeId id) {
    Objects.requireNonNull(id, "id");
    Node node = get(id);
    node.drain();
    nodes.save(NodeMapper.toEntity(node));
  }

  @Override
  public void heartbeat(NodeId id) {
    Objects.requireNonNull(id, "id");
    Node node = get(id);
    node.recordHeartbeat();
    nodes.save(NodeMapper.toEntity(node));
  }

  @Override
  public void markUnhealthy(NodeId id, String reason) {
    Objects.requireNonNull(id, "id");
    Node node = get(id);
    node.markUnhealthy(reason);
    nodes.save(NodeMapper.toEntity(node));
  }
}
