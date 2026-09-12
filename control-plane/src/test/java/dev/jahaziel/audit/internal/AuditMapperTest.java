/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.audit.persistence.AuditEventEntity;
import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link AuditMapper} with stubbed rows. */
class AuditMapperTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final UUID eventId = UUID.randomUUID();
  private final Instant timestamp = Instant.parse("2026-01-02T03:04:05Z");

  @Test
  void toEntitySerializesDetailsAndNullsEmptyOptionals() {
    AuditEventEntity row = AuditMapper.toEntity(record(), mapper);
    assertEquals("{\"k\":\"v\"}", row.getDetails());
    assertNull(row.getTarget());
    assertNull(row.getTargetType());
    assertNull(row.getSourceIp());
    assertNull(row.getSourceNode());
    assertNull(row.getPrevHash());
    assertEquals("USER", row.getActorType());
    assertEquals("SUCCESS", row.getOutcome());
    assertEquals(eventId, row.getId().getEventId());
    assertEquals(timestamp, row.getId().getTimestamp());
  }

  @Test
  void roundTripRestoresRecord() {
    AuditRecord record = record();
    assertEquals(record, AuditMapper.toDomain(AuditMapper.toEntity(record, mapper), mapper));
  }

  @Test
  void toDomainMapsNullsToEmptyOptionals() {
    AuditEventEntity row = mock(AuditEventEntity.class);
    AuditEventEntity.Id key = mock(AuditEventEntity.Id.class);
    when(row.getId()).thenReturn(key);
    when(key.getEventId()).thenReturn(eventId);
    when(key.getTimestamp()).thenReturn(timestamp);
    when(row.getActor()).thenReturn("user:x");
    when(row.getActorType()).thenReturn("USER");
    when(row.getAction()).thenReturn("workspace.start");
    when(row.getTarget()).thenReturn(null);
    when(row.getTargetType()).thenReturn(null);
    when(row.getOutcome()).thenReturn("SUCCESS");
    when(row.getSourceIp()).thenReturn(null);
    when(row.getSourceNode()).thenReturn(null);
    when(row.getDetails()).thenReturn("{}");
    when(row.getPrevHash()).thenReturn(null);
    when(row.getHash()).thenReturn("h1");
    AuditRecord record = AuditMapper.toDomain(row, mapper);
    assertEquals(Optional.empty(), record.target());
    assertEquals(Optional.empty(), record.targetType());
    assertEquals(Optional.empty(), record.sourceIp());
    assertEquals(Optional.empty(), record.sourceNode());
    assertEquals(Optional.empty(), record.prevHash());
    assertEquals(Map.of(), record.details());
  }

  @Test
  void corruptDetailsThrow() {
    AuditEventEntity row = mock(AuditEventEntity.class);
    AuditEventEntity.Id key = mock(AuditEventEntity.Id.class);
    when(row.getId()).thenReturn(key);
    when(key.getEventId()).thenReturn(eventId);
    when(key.getTimestamp()).thenReturn(timestamp);
    when(row.getActor()).thenReturn("user:x");
    when(row.getActorType()).thenReturn("USER");
    when(row.getAction()).thenReturn("act");
    when(row.getOutcome()).thenReturn("SUCCESS");
    when(row.getDetails()).thenReturn("not-json");
    assertThrows(IllegalArgumentException.class, () -> AuditMapper.toDomain(row, mapper));
  }

  private AuditRecord record() {
    return new AuditRecord(
        eventId,
        timestamp,
        "user:x",
        ActorType.USER,
        "workspace.start",
        Optional.empty(),
        Optional.empty(),
        Outcome.SUCCESS,
        Optional.empty(),
        Optional.empty(),
        Map.of("k", "v"),
        Optional.empty(),
        "h1");
  }
}
