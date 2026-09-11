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
public record AuditRecordDto(
    UUID eventId,
    Instant timestamp,
    String actor,
    String actorType,
    String action,
    Optional<String> target,
    String outcome,
    String hash) {
  /**
   * Maps a record to a DTO.
   *
   * @param record the record
   * @return the DTO
   */
  public static AuditRecordDto from(AuditRecord record) {
    return new AuditRecordDto(
        record.eventId(),
        record.timestamp(),
        record.actor(),
        record.actorType().name(),
        record.action(),
        record.target(),
        record.outcome().name(),
        record.hash());
  }
}
