/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.audit.persistence.AuditEventEntity;
import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class AuditMapperCoverageGapTest {
  @Test
  void toEntitySerializationFailureThrows() {
    ObjectMapper failing = mock(ObjectMapper.class);
    when(failing.writeValueAsString(any())).thenThrow(mock(JacksonException.class));
    AuditRecord record =
        new AuditRecord(
            UUID.randomUUID(),
            Instant.parse("2026-01-02T03:04:05Z"),
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
    assertThrows(IllegalArgumentException.class, () -> AuditMapper.toEntity(record, failing));
  }

  @Test
  void toDomainNullDetailsYieldsEmpty() {
    ObjectMapper mapper = new ObjectMapper();
    AuditEventEntity row = new AuditEventEntity();
    UUID eventId = UUID.randomUUID();
    Instant timestamp = Instant.parse("2026-01-02T03:04:05Z");
    row.setId(new AuditEventEntity.Id(eventId, timestamp));
    row.setActor("user:x");
    row.setActorType("USER");
    row.setAction("workspace.start");
    row.setOutcome("SUCCESS");
    row.setDetails(null);
    row.setHash("h1");
    AuditRecord record = AuditMapper.toDomain(row, mapper);
    assertEquals(Map.of(), record.details());
    assertEquals(Optional.empty(), record.prevHash());
  }
}
