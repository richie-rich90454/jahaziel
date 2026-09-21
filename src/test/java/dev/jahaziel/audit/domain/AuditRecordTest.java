/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link AuditRecord}. */
class AuditRecordTest {
  @Test
  void storesEventWithChainLink() {
    UUID eventId = UUID.randomUUID();
    Instant timestamp = Instant.now();
    AuditRecord record =
        new AuditRecord(
            eventId,
            timestamp,
            "user:x",
            ActorType.USER,
            "workspace.start",
            Optional.of("ws:x"),
            Optional.of("workspace"),
            Outcome.SUCCESS,
            Optional.of("10.0.0.1"),
            Optional.of("node-a"),
            Map.of("port", "8080"),
            Optional.of("prev"),
            "hash");
    assertEquals(eventId, record.eventId());
    assertEquals("hash", record.hash());
    assertEquals(Optional.of("prev"), record.prevHash());
  }

  @Test
  void nullsAreRejectedAndDetailsCopied() {
    assertThrows(
        NullPointerException.class,
        () ->
            new AuditRecord(
                null,
                Instant.now(),
                "a",
                ActorType.SYSTEM,
                "act",
                Optional.empty(),
                Optional.empty(),
                Outcome.SUCCESS,
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                Optional.empty(),
                "h"));
    Map<String, String> details = new HashMap<>(Map.of("k", "v"));
    AuditRecord record =
        new AuditRecord(
            UUID.randomUUID(),
            Instant.now(),
            "a",
            ActorType.SYSTEM,
            "act",
            Optional.empty(),
            Optional.empty(),
            Outcome.SUCCESS,
            Optional.empty(),
            Optional.empty(),
            details,
            Optional.empty(),
            "h");
    details.put("evil", "x");
    assertEquals(Map.of("k", "v"), record.details());
  }
}
