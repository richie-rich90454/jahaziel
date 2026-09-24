/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.audit.persistence.AuditEventRepository;
import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.domain.Pageable;

class AuditServiceCoverageGapTest {
  @Test
  void appendWithoutShaThrowsState() {
    AuditEventRepository events = mock(AuditEventRepository.class);
    when(events.findLatest(anyPageable())).thenReturn(List.of());
    AuditServiceImpl service = new AuditServiceImpl(events);
    try (MockedStatic<MessageDigest> digest = mockStatic(MessageDigest.class)) {
      digest
          .when(() -> MessageDigest.getInstance("SHA-256"))
          .thenThrow(new NoSuchAlgorithmException("gone"));
      assertThrows(IllegalStateException.class, () -> service.append(input()));
    }
  }

  private static Pageable anyPageable() {
    return org.mockito.ArgumentMatchers.any();
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
