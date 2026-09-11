/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** One host running a node agent. A single-node cluster is a cluster with one member. */
public final class Node {
  private final NodeId id;
  private final NodeCapacity capacity;
  private NodeState state;
  private final Set<WorkspaceId> workspaces = new HashSet<>();
  private NodeMetrics metrics = NodeMetrics.zero();

  /**
   * Registers a healthy node.
   *
   * @param id the node identifier
   * @param capacity the total capacity
   */
  public Node(NodeId id, NodeCapacity capacity) {
    this.id = Objects.requireNonNull(id, "id");
    this.capacity = Objects.requireNonNull(capacity, "capacity");
    this.state = new NodeState.Healthy(Instant.now());
  }

  /** Returns the node identifier. */
  public NodeId id() {
    return id;
  }

  /** Returns the total capacity. */
  public NodeCapacity capacity() {
    return capacity;
  }

  /** Returns the current state. */
  public NodeState state() {
    return state;
  }

  /**
   * Returns the placed workspaces.
   *
   * @return a defensive copy
   */
  public Set<WorkspaceId> workspaces() {
    return Set.copyOf(workspaces);
  }

  /** Returns the latest resource sample. */
  public NodeMetrics metrics() {
    return metrics;
  }

  /**
   * Places a workspace on this node.
   *
   * @param workspaceId the workspace to place
   */
  public void assign(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    if (state instanceof NodeState.Draining || state instanceof NodeState.Unhealthy) {
      throw new IllegalStateException("Node " + id + " cannot place while " + state);
    }
    if (!workspaces.add(workspaceId)) {
      throw new IllegalArgumentException("Workspace " + workspaceId + " already placed");
    }
  }

  /**
   * Removes a workspace from this node.
   *
   * @param workspaceId the workspace to remove
   */
  public void release(WorkspaceId workspaceId) {
    if (!workspaces.remove(workspaceId)) {
      throw new IllegalArgumentException("Workspace " + workspaceId + " not placed");
    }
  }

  /**
   * Marks the node degraded.
   *
   * @param reason the degradation reason
   */
  public void degrade(String reason) {
    if (state instanceof NodeState.Draining || state instanceof NodeState.Unhealthy) {
      throw new IllegalStateException("Node " + id + " cannot degrade while " + state);
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
    state = new NodeState.Degraded(Instant.now(), reason);
  }

  /** Marks a degraded node healthy. */
  public void recover() {
    if (!(state instanceof NodeState.Degraded)) {
      throw new IllegalStateException("Node " + id + " cannot recover while " + state);
    }
    state = new NodeState.Healthy(Instant.now());
  }

  /** Starts draining. Placed workspaces stay; no new placements. */
  public void drain() {
    if (state instanceof NodeState.Draining || state instanceof NodeState.Unhealthy) {
      throw new IllegalStateException("Node " + id + " cannot drain while " + state);
    }
    state = new NodeState.Draining(Instant.now());
  }

  /**
   * Records a resource sample.
   *
   * @param sample the latest sample
   */
  public void updateMetrics(NodeMetrics sample) {
    metrics = Objects.requireNonNull(sample, "sample");
  }
}
