/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.audit.domain.AuditRecord;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One audit event for reads.
 *
 * @param eventId the event identifier
 * @param timestamp when the action happened
 * @param actor who performed the action
 * @param actorType what kind of actor performed the action
 * @param action what happened
 * @param target what the action targeted
 * @param outcome how the action ended
 * @param hash hash of this batch
 */
public final class AuditRecordDto {

    private final UUID eventId;

    private final Instant timestamp;

    private final String actor;

    private final String actorType;

    private final String action;

    private final Optional<String> target;

    private final String outcome;

    private final String hash;

    public AuditRecordDto(
            UUID eventId,
            Instant timestamp,
            String actor,
            String actorType,
            String action,
            Optional<String> target,
            String outcome,
            String hash) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.actor = actor;
        this.actorType = actorType;
        this.action = action;
        this.target = target;
        this.outcome = outcome;
        this.hash = hash;
    }

    /**
     * Maps an audit entry to a DTO.
     *
     * @param entry the audit entry
     * @return the DTO
     */
    public static AuditRecordDto from(AuditRecord entry) {
        return new AuditRecordDto(
                entry.eventId(),
                entry.timestamp(),
                entry.actor(),
                entry.actorType().name(),
                entry.action(),
                entry.target(),
                entry.outcome().name(),
                entry.hash());
    }

    public UUID eventId() {
        return this.eventId;
    }

    public UUID getEventId() {
        return this.eventId;
    }

    public Instant timestamp() {
        return this.timestamp;
    }

    public Instant getTimestamp() {
        return this.timestamp;
    }

    public String actor() {
        return this.actor;
    }

    public String getActor() {
        return this.actor;
    }

    public String actorType() {
        return this.actorType;
    }

    public String getActorType() {
        return this.actorType;
    }

    public String action() {
        return this.action;
    }

    public String getAction() {
        return this.action;
    }

    public Optional<String> target() {
        return this.target;
    }

    public Optional<String> getTarget() {
        return this.target;
    }

    public String outcome() {
        return this.outcome;
    }

    public String getOutcome() {
        return this.outcome;
    }

    public String hash() {
        return this.hash;
    }

    public String getHash() {
        return this.hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditRecordDto)) return false;
        AuditRecordDto other = (AuditRecordDto) o;
        return (java.util.Objects.equals(this.eventId, other.eventId))
                && java.util.Objects.equals(this.timestamp, other.timestamp)
                && java.util.Objects.equals(this.actor, other.actor)
                && java.util.Objects.equals(this.actorType, other.actorType)
                && java.util.Objects.equals(this.action, other.action)
                && java.util.Objects.equals(this.target, other.target)
                && java.util.Objects.equals(this.outcome, other.outcome)
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
                this.outcome,
                this.hash);
    }

    @Override
    public String toString() {
        return "AuditRecordDto["
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
                + "outcome="
                + this.outcome
                + ", "
                + "hash="
                + this.hash
                + "]";
    }
}
