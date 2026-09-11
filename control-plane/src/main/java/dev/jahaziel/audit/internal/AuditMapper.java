/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.audit.persistence.AuditEventEntity;
import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps audit rows to domain objects and back. Pure static functions; the mapping lives at the
 * boundary, not in the domain.
 */
public final class AuditMapper {
  private AuditMapper() {}

  /**
   * Maps a record to its row, serializing details to JSON.
   *
   * @param record the record
   * @param mapper the JSON codec
   * @return the row
   * @throws NullPointerException when any argument is null
   * @throws IllegalArgumentException when details cannot be serialized
   */
  public static AuditEventEntity toEntity(AuditRecord record, ObjectMapper mapper) {
    Objects.requireNonNull(record, "record");
    Objects.requireNonNull(mapper, "mapper");
    AuditEventEntity row = new AuditEventEntity();
    row.setId(new AuditEventEntity.Id(record.eventId(), record.timestamp()));
    row.setActor(record.actor());
    row.setActorType(record.actorType().name());
    row.setAction(record.action());
    row.setTarget(record.target().orElse(null));
    row.setTargetType(record.targetType().orElse(null));
    row.setOutcome(record.outcome().name());
    row.setSourceIp(record.sourceIp().orElse(null));
    row.setSourceNode(record.sourceNode().orElse(null));
    row.setDetails(writeDetails(record.details(), mapper));
    row.setPrevHash(record.prevHash().orElse(null));
    row.setHash(record.hash());
    return row;
  }

  /**
   * Rebuilds a record from its row, parsing details from JSON.
   *
   * @param row the row
   * @param mapper the JSON codec
   * @return the record
   * @throws NullPointerException when any argument is null
   * @throws IllegalArgumentException when details cannot be parsed
   */
  public static AuditRecord toDomain(AuditEventEntity row, ObjectMapper mapper) {
    Objects.requireNonNull(row, "row");
    Objects.requireNonNull(mapper, "mapper");
    return new AuditRecord(
        row.getId().getEventId(),
        row.getId().getTimestamp(),
        row.getActor(),
        ActorType.valueOf(row.getActorType()),
        row.getAction(),
        Optional.ofNullable(row.getTarget()),
        Optional.ofNullable(row.getTargetType()),
        Outcome.valueOf(row.getOutcome()),
        Optional.ofNullable(row.getSourceIp()),
        Optional.ofNullable(row.getSourceNode()),
        readDetails(row.getDetails(), mapper),
        Optional.ofNullable(row.getPrevHash()),
        row.getHash());
  }

  private static String writeDetails(Map<String, String> details, ObjectMapper mapper) {
    try {
      return mapper.writeValueAsString(details);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Audit details cannot be serialized", e);
    }
  }

  private static Map<String, String> readDetails(String json, ObjectMapper mapper) {
    if (json == null) {
      return Map.of();
    }
    try {
      return mapper.readValue(json, new TypeReference<Map<String, String>>() {});
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Audit details cannot be parsed", e);
    }
  }
}
