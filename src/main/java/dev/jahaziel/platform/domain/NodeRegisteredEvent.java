/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A node registered.
 *
 * @param nodeId the node
 * @param occurredAt when the event occurred
 */
public final class NodeRegisteredEvent extends DomainEvent {

    private final String nodeId;

    private final Instant occurredAt;

    public NodeRegisteredEvent(String nodeId, Instant occurredAt) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        this.nodeId = nodeId;
        this.occurredAt = occurredAt;
    }

    public String nodeId() {
        return this.nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public Instant occurredAt() {
        return this.occurredAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeRegisteredEvent)) return false;
        NodeRegisteredEvent other = (NodeRegisteredEvent) o;
        return (java.util.Objects.equals(this.nodeId, other.nodeId))
                && java.util.Objects.equals(this.occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.nodeId, this.occurredAt);
    }

    @Override
    public String toString() {
        return "NodeRegisteredEvent["
                + "nodeId="
                + this.nodeId
                + ", "
                + "occurredAt="
                + this.occurredAt
                + "]";
    }
}
