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
public record AuditEvent(
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
  public AuditEvent {
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
  }
}
