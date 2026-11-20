/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One append-only audit event with a hash chain link.
 *
 * @param eventId the event identifier
 * @param timestamp when the action happened
 * @param actor who performed the action
 * @param actorType what kind of actor performed the action
 * @param action what happened
 * @param target what the action targeted, empty when there is no target
 * @param targetType the kind of target, empty when there is no target
 * @param outcome how the action ended
 * @param sourceIp the source address, empty when unknown
 * @param sourceNode the source node, empty when unknown
 * @param details extra structured detail
 * @param prevHash hash of the previous batch, empty for the first batch
 * @param hash hash of this batch
 */
public final class AuditEvent {

    private final UUID eventId;

    private final Instant timestamp;

    private final String actor;

    private final ActorType actorType;

    private final String action;

    private final Optional<String> target;

    private final Optional<String> targetType;

    private final Outcome outcome;

    private final Optional<String> sourceIp;

    private final Optional<String> sourceNode;

    private final Map<String, String> details;

    private final Optional<String> prevHash;

    private final String hash;

    public AuditEvent(
            UUID eventId,
            Instant timestamp,
            String actor,
            ActorType actorType,
            String action,
            Optional<String> target,
            Optional<String> targetType,
            Outcome outcome,
            Optional<String> sourceIp,
            Optional<String> sourceNode,
            Map<String, String> details,
            Optional<String> prevHash,
            String hash) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(sourceIp, "sourceIp");
        Objects.requireNonNull(sourceNode, "sourceNode");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(prevHash, "prevHash");
        Objects.requireNonNull(hash, "hash");
        details = Map.copyOf(details);
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.actor = actor;
        this.actorType = actorType;
        this.action = action;
        this.target = target;
        this.targetType = targetType;
        this.outcome = outcome;
        this.sourceIp = sourceIp;
        this.sourceNode = sourceNode;
        this.details = details;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    public UUID eventId() {
        return this.eventId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant timestamp() {
        return this.timestamp;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String actor() {
        return this.actor;
    }

    public String getActor() {
        return actor;
    }

    public ActorType actorType() {
        return this.actorType;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String action() {
        return this.action;
    }

    public String getAction() {
        return action;
    }

    public Optional<String> target() {
        return this.target;
    }

    public Optional<String> getTarget() {
        return target;
    }

    public Optional<String> targetType() {
        return this.targetType;
    }

    public Optional<String> getTargetType() {
        return targetType;
    }

    public Outcome outcome() {
        return this.outcome;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Optional<String> sourceIp() {
        return this.sourceIp;
    }

    public Optional<String> getSourceIp() {
        return sourceIp;
    }

    public Optional<String> sourceNode() {
        return this.sourceNode;
    }

    public Optional<String> getSourceNode() {
        return sourceNode;
    }

    public Map<String, String> details() {
        return this.details;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public Optional<String> prevHash() {
        return this.prevHash;
    }

    public Optional<String> getPrevHash() {
        return prevHash;
    }

    public String hash() {
        return this.hash;
    }

    public String getHash() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditEvent)) return false;
        AuditEvent other = (AuditEvent) o;
        return (java.util.Objects.equals(this.eventId, other.eventId))
                && java.util.Objects.equals(this.timestamp, other.timestamp)
                && java.util.Objects.equals(this.actor, other.actor)
                && java.util.Objects.equals(this.actorType, other.actorType)
                && java.util.Objects.equals(this.action, other.action)
                && java.util.Objects.equals(this.target, other.target)
                && java.util.Objects.equals(this.targetType, other.targetType)
                && java.util.Objects.equals(this.outcome, other.outcome)
                && java.util.Objects.equals(this.sourceIp, other.sourceIp)
                && java.util.Objects.equals(this.sourceNode, other.sourceNode)
                && java.util.Objects.equals(this.details, other.details)
                && java.util.Objects.equals(this.prevHash, other.prevHash)
                && java.util.Objects.equals(this.hash, other.hash);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                this.eventId,
                this.timestamp,
                this.actor,
                this.actorType,
                this.action,
                this.target,
                this.targetType,
                this.outcome,
                this.sourceIp,
                this.sourceNode,
                this.details,
                this.prevHash,
                this.hash);
    }

    @Override
    public String toString() {
        return "AuditEvent["
                + "eventId="
                + this.eventId
                + ", "
                + "timestamp="
                + this.timestamp
                + ", "
                + "actor="
                + this.actor
                + ", "
                + "actorType="
                + this.actorType
                + ", "
                + "action="
                + this.action
                + ", "
                + "target="
                + this.target
                + ", "
                + "targetType="
                + this.targetType
                + ", "
                + "outcome="
                + this.outcome
                + ", "
                + "sourceIp="
                + this.sourceIp
                + ", "
                + "sourceNode="
                + this.sourceNode
                + ", "
                + "details="
                + this.details
                + ", "
                + "prevHash="
                + this.prevHash
                + ", "
                + "hash="
                + this.hash
                + "]";
    }
}
