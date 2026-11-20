/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A node was lost.
 *
 * @param nodeId the node
 * @param reason why the node is considered lost
 * @param occurredAt when the event occurred
 */
public final class NodeLostEvent extends DomainEvent {

    private final String nodeId;

    private final String reason;

    private final Instant occurredAt;

    public NodeLostEvent(String nodeId, String reason, Instant occurredAt) {
        Objects.requireNonNull(nodeId, "nodeId");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        this.nodeId = nodeId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public String nodeId() {
        return this.nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String reason() {
        return this.reason;
    }

    public String getReason() {
        return reason;
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
        if (!(o instanceof NodeLostEvent)) return false;
        NodeLostEvent other = (NodeLostEvent) o;
        return (java.util.Objects.equals(this.nodeId, other.nodeId))
                && java.util.Objects.equals(this.reason, other.reason)
                && java.util.Objects.equals(this.occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.nodeId, this.reason, this.occurredAt);
    }

    @Override
    public String toString() {
        return "NodeLostEvent["
                + "nodeId="
                + this.nodeId
                + ", "
                + "reason="
                + this.reason
                + ", "
                + "occurredAt="
                + this.occurredAt
                + "]";
    }
}
