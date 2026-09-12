/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.audit.persistence.AuditEventEntity;
import dev.jahaziel.audit.persistence.AuditEventRepository;
import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

/** Behavior of {@link AuditServiceImpl} with stubbed rows. */
class AuditServiceImplTest {
  private AuditEventRepository events;
  private AuditServiceImpl service;

  @BeforeEach
  void setUp() {
    events = mock(AuditEventRepository.class);
    service = new AuditServiceImpl(events);
  }

  @Test
  void appendOnEmptyStoreLinksGenesis() {
    when(events.findLatest(any(Pageable.class))).thenReturn(List.of());
    AuditRecord stored = service.append(input());
    ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
    verify(events).save(captor.capture());
    assertNull(captor.getValue().getPrevHash());
    assertEquals(Optional.empty(), stored.prevHash());
    assertEquals(captor.getValue().getHash(), stored.hash());
  }

  @Test
  void secondAppendLinksToFirstHash() {
    AuditEventEntity first = mock(AuditEventEntity.class);
    when(first.getHash()).thenReturn("abc123");
    when(events.findLatest(any(Pageable.class))).thenReturn(List.of(first));
    AuditRecord stored = service.append(input());
    ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
    verify(events).save(captor.capture());
    assertEquals("abc123", captor.getValue().getPrevHash());
    assertEquals(Optional.of("abc123"), stored.prevHash());
    assertEquals(captor.getValue().getHash(), stored.hash());
  }

  @Test
  void latestRejectsNonPositiveLimit() {
    assertThrows(IllegalArgumentException.class, () -> service.latest(0));
    assertThrows(IllegalArgumentException.class, () -> service.latest(-1));
  }

  @Test
  void latestMapsRows() {
    AuditEventEntity row = mock(AuditEventEntity.class);
    AuditEventEntity.Id key = mock(AuditEventEntity.Id.class);
    UUID eventId = UUID.randomUUID();
    when(row.getId()).thenReturn(key);
    when(key.getEventId()).thenReturn(eventId);
    when(key.getTimestamp()).thenReturn(Instant.parse("2026-01-02T03:04:05Z"));
    when(row.getActor()).thenReturn("user:x");
    when(row.getActorType()).thenReturn("USER");
    when(row.getAction()).thenReturn("workspace.start");
    when(row.getTarget()).thenReturn("ws:x");
    when(row.getTargetType()).thenReturn("workspace");
    when(row.getOutcome()).thenReturn("SUCCESS");
    when(row.getSourceIp()).thenReturn("10.0.0.1");
    when(row.getSourceNode()).thenReturn("node-a");
    when(row.getDetails()).thenReturn("{\"port\":\"8080\"}");
    when(row.getPrevHash()).thenReturn(null);
    when(row.getHash()).thenReturn("h1");
    when(events.findLatest(any(Pageable.class))).thenReturn(List.of(row));
    List<AuditRecord> records = service.latest(1);
    assertEquals(1, records.size());
    assertEquals(eventId, records.get(0).eventId());
    assertEquals(Map.of("port", "8080"), records.get(0).details());
    assertEquals(Optional.empty(), records.get(0).prevHash());
  }

  private static AuditRecord input() {
    return new AuditRecord(
        UUID.randomUUID(),
        Instant.parse("2026-01-02T03:04:05Z"),
        "user:x",
        ActorType.USER,
        "workspace.start",
        Optional.of("ws:x"),
        Optional.of("workspace"),
        Outcome.SUCCESS,
        Optional.of("10.0.0.1"),
        Optional.of("node-a"),
        Map.of("port", "8080"),
        Optional.empty(),
        "ignored");
  }
}
