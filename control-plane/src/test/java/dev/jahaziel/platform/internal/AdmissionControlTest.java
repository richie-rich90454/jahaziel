/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.jahaziel.platform.api.AdmissionDecision;
import org.junit.jupiter.api.Test;

/** Behavior of {@link AdmissionControl} on a 48 GiB host. */
class AdmissionControlTest {
  private static final long TOTAL = 49152;
  private static final long RESERVED = 4096;

  @Test
  void admitsWhenRoom() {
    AdmissionDecision decision =
        AdmissionControl.evaluate(TOTAL, 0, 2048, RESERVED, RESERVED, RESERVED, 1024, 0.9);
    assertEquals(new AdmissionDecision.Admit(2048), decision);
  }

  @Test
  void admitsSoftFloorUnderPressure() {
    long started = TOTAL - RESERVED * 3 - 512;
    AdmissionDecision decision =
        AdmissionControl.evaluate(TOTAL, started, 2048, RESERVED, RESERVED, RESERVED, 1024, 0.9);
    assertInstanceOf(AdmissionDecision.Admit.class, decision);
    assertEquals(1024, ((AdmissionDecision.Admit) decision).memoryMib());
  }

  @Test
  void refusesWhenFull() {
    long started = (long) (TOTAL * 0.9);
    AdmissionDecision decision =
        AdmissionControl.evaluate(TOTAL, started, 2048, RESERVED, RESERVED, RESERVED, 1024, 0.9);
    assertInstanceOf(AdmissionDecision.Refuse.class, decision);
  }
}
