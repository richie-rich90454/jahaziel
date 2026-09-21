/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One host running a node agent. A single-node cluster is a cluster with one member. */
public final class Node {
  private final NodeId id;
  private final String name;
  private final String region;
  private final NodeCapacity capacity;
  private final String certFingerprint;
  private final Instant registeredAt;
  private NodeState state;
  private final Set<UUID> workspaces = new HashSet<>();
  private NodeMetrics metrics = NodeMetrics.zero();
  private Instant lastHeartbeat;

  /**
   * Registers a healthy node.
   *
   * @param id the node identifier
   * @param name the human-readable name
   * @param region the hosting region
   * @param capacity the total capacity
   * @param certFingerprint the agent certificate fingerprint
   */
  public Node(
      NodeId id, String name, String region, NodeCapacity capacity, String certFingerprint) {
    this(id, name, region, capacity, certFingerprint, Instant.now(), null);
  }

  /**
   * Rebuilds a node from stored values.
   *
   * @param id the node identifier
   * @param name the human-readable name
   * @param region the hosting region
   * @param capacity the total capacity
   * @param certFingerprint the agent certificate fingerprint
   * @param registeredAt when the node registered
   * @param lastHeartbeat the last heartbeat, or null when never seen
   */
  public Node(
      NodeId id,
      String name,
      String region,
      NodeCapacity capacity,
      String certFingerprint,
      Instant registeredAt,
      Instant lastHeartbeat) {
    this.id = Objects.requireNonNull(id, "id");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    this.name = name;
    if (region == null || region.isBlank()) {
      throw new IllegalArgumentException("region must not be blank");
    }
    this.region = region;
    this.capacity = Objects.requireNonNull(capacity, "capacity");
    if (certFingerprint == null || certFingerprint.isBlank()) {
      throw new IllegalArgumentException("certFingerprint must not be blank");
    }
    this.certFingerprint = certFingerprint;
    this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
    this.lastHeartbeat = lastHeartbeat;
    this.state = new NodeState.Healthy(Instant.now());
  }

  /** Returns the node identifier. */
  public NodeId id() {
    return id;
  }

  /** Returns the human-readable name. */
  public String name() {
    return name;
  }

  /** Returns the hosting region. */
  public String region() {
    return region;
  }

  /** Returns the total capacity. */
  public NodeCapacity capacity() {
    return capacity;
  }

  /** Returns the agent certificate fingerprint. */
  public String certFingerprint() {
    return certFingerprint;
  }

  /** Returns when the node registered. */
  public Instant registeredAt() {
    return registeredAt;
  }

  /** Returns the last heartbeat, empty when never seen. */
  public Optional<Instant> lastHeartbeat() {
    return Optional.ofNullable(lastHeartbeat);
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
  public Set<UUID> workspaces() {
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
  public void assign(UUID workspaceId) {
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
  public void release(UUID workspaceId) {
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
   * Marks the node unhealthy, for example on missed heartbeats.
   *
   * @param reason why the node is considered lost
   */
  public void markUnhealthy(String reason) {
    if (state instanceof NodeState.Unhealthy) {
      throw new IllegalStateException("Node " + id + " is already unhealthy");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
    state = new NodeState.Unhealthy(Instant.now(), reason);
  }

  /** Records a heartbeat now. */
  public void recordHeartbeat() {
    lastHeartbeat = Instant.now();
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
