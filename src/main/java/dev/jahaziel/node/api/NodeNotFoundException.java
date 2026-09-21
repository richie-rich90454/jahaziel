/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.node.api;

import dev.jahaziel.node.domain.NodeId;

/** Thrown when a node does not exist. */
public final class NodeNotFoundException extends RuntimeException {
  private final NodeId nodeId;

  /**
   * Creates the exception.
   *
   * @param nodeId the missing node identifier
   */
  public NodeNotFoundException(NodeId nodeId) {
    super("Node " + nodeId + " not found");
    this.nodeId = nodeId;
  }

  /** Returns the missing node identifier. */
  public NodeId nodeId() {
    return nodeId;
  }
}
