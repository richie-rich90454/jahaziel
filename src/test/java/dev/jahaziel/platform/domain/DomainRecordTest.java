/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Null guards of the domain event records. */
class DomainRecordTest {
  private final Instant at = Instant.now();

  @Test
  void nodeLostGuardsNullReason() {
    assertThrows(IllegalArgumentException.class, () -> new NodeLostEvent("n", null, at));
  }

  @Test
  void policyChangedGuardsNullScope() {
    assertThrows(IllegalArgumentException.class, () -> new PolicyChangedEvent(null, "", at));
  }

  @Test
  void securityViolationGuardsNullType() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SecurityViolationEvent(UUID.randomUUID(), null, Map.of(), at));
  }
}
