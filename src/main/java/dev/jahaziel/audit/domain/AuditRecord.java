/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.domain;

import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One append-only audit event with a hash chain link. Actor vocabulary is shared with the plugin
 * types.
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
public final class AuditRecord {
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

    /** Creates the event, copying the detail map and validating every required component. */
    public AuditRecord(
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
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.actorType = Objects.requireNonNull(actorType, "actorType");
        this.action = Objects.requireNonNull(action, "action");
        this.target = Objects.requireNonNull(target, "target");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.sourceIp = Objects.requireNonNull(sourceIp, "sourceIp");
        this.sourceNode = Objects.requireNonNull(sourceNode, "sourceNode");
        this.details = Map.copyOf(Objects.requireNonNull(details, "details"));
        this.prevHash = Objects.requireNonNull(prevHash, "prevHash");
        this.hash = Objects.requireNonNull(hash, "hash");
    }

    /** Returns the event identifier. */
    public UUID eventId() {
        return eventId;
    }

    public UUID getEventId() {
        return eventId;
    }

    /** Returns when the action happened. */
    public Instant timestamp() {
        return timestamp;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /** Returns who performed the action. */
    public String actor() {
        return actor;
    }

    public String getActor() {
        return actor;
    }

    /** Returns what kind of actor performed the action. */
    public ActorType actorType() {
        return actorType;
    }

    public ActorType getActorType() {
        return actorType;
    }

    /** Returns what happened. */
    public String action() {
        return action;
    }

    public String getAction() {
        return action;
    }

    /** Returns the optional target. */
    public Optional<String> target() {
        return target;
    }

    public Optional<String> getTarget() {
        return target;
    }

    /** Returns the optional target type. */
    public Optional<String> targetType() {
        return targetType;
    }

    public Optional<String> getTargetType() {
        return targetType;
    }

    /** Returns how the action ended. */
    public Outcome outcome() {
        return outcome;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    /** Returns the optional source address. */
    public Optional<String> sourceIp() {
        return sourceIp;
    }

    public Optional<String> getSourceIp() {
        return sourceIp;
    }

    /** Returns the optional source node. */
    public Optional<String> sourceNode() {
        return sourceNode;
    }

    public Optional<String> getSourceNode() {
        return sourceNode;
    }

    /** Returns the structured detail, as an unmodifiable map. */
    public Map<String, String> details() {
        return details;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    /** Returns the hash of the previous batch. */
    public Optional<String> prevHash() {
        return prevHash;
    }

    public Optional<String> getPrevHash() {
        return prevHash;
    }

    /** Returns the hash of this batch. */
    public String hash() {
        return hash;
    }

    public String getHash() {
        return hash;
    }

    /** Compares by class and every component. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AuditRecord other = (AuditRecord) o;
        return Objects.equals(eventId, other.eventId)
                && Objects.equals(timestamp, other.timestamp)
                && Objects.equals(actor, other.actor)
                && Objects.equals(actorType, other.actorType)
                && Objects.equals(action, other.action)
                && Objects.equals(target, other.target)
                && Objects.equals(targetType, other.targetType)
                && Objects.equals(outcome, other.outcome)
                && Objects.equals(sourceIp, other.sourceIp)
                && Objects.equals(sourceNode, other.sourceNode)
                && Objects.equals(details, other.details)
                && Objects.equals(prevHash, other.prevHash)
                && Objects.equals(hash, other.hash);
    }

    /** Returns a hash derived from every component. */
    @Override
    public int hashCode() {
        return Objects.hash(
                eventId,
                timestamp,
                actor,
                actorType,
                action,
                target,
                targetType,
                outcome,
                sourceIp,
                sourceNode,
                details,
                prevHash,
                hash);
    }

    /** Returns a readable representation. */
    @Override
    public String toString() {
        return "AuditRecord[eventId="
                + eventId
                + ", timestamp="
                + timestamp
                + ", actor="
                + actor
                + ", actorType="
                + actorType
                + ", action="
                + action
                + ", target="
                + target
                + ", targetType="
                + targetType
                + ", outcome="
                + outcome
                + ", sourceIp="
                + sourceIp
                + ", sourceNode="
                + sourceNode
                + ", details="
                + details
                + ", prevHash="
                + prevHash
                + ", hash="
                + hash
                + "]";
    }
}
