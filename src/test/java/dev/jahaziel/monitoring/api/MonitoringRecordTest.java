/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Validation of {@link HourlyRollup}. */
class MonitoringRecordTest {
  private HourlyRollup valid() {
    return new HourlyRollup(
        UUID.randomUUID(), Instant.now(), 1.0f, 2.0f, 10L, 20L, 1L, 2L, 3L, 4L, 5);
  }

  @Test
  void storesFields() {
    HourlyRollup rollup = valid();
    assertEquals(1.0f, rollup.cpuAvgPercent(), 0.0001f);
    assertEquals(2.0f, rollup.cpuMaxPercent(), 0.0001f);
    assertEquals(10L, rollup.memoryAvgMib());
    assertEquals(5, rollup.processMax());
  }

  @Test
  void guardsNulls() {
    HourlyRollup probe = valid();
    assertThrows(
        NullPointerException.class,
        () -> new HourlyRollup(null, probe.hourStart(), 0, 0, 0, 0, 0, 0, 0, 0, 0));
    assertThrows(
        NullPointerException.class,
        () -> new HourlyRollup(probe.workspaceId(), null, 0, 0, 0, 0, 0, 0, 0, 0, 0));
  }

  @Test
  void guardsNegativeCpu() {
    HourlyRollup probe = valid();
    assertThrows(
        IllegalArgumentException.class,
        () -> new HourlyRollup(probe.workspaceId(), probe.hourStart(), -1, 0, 0, 0, 0, 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HourlyRollup(probe.workspaceId(), probe.hourStart(), 0, -1, 0, 0, 0, 0, 0, 0, 0));
  }

  @Test
  void guardsNegativeMemoryDiskNetworkAndProcesses() {
    HourlyRollup probe = valid();
    assertThrows(
        IllegalArgumentException.class,
        () -> new HourlyRollup(probe.workspaceId(), probe.hourStart(), 0, 0, -1, 0, 0, 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HourlyRollup(probe.workspaceId(), probe.hourStart(), 0, 0, 0, -1, 0, 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HourlyRollup(probe.workspaceId(), probe.hourStart(), 0, 0, 0, 0, -1, 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HourlyRollup(probe.workspaceId(), probe.hourStart(), 0, 0, 0, 0, 0, -1, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HourlyRollup(probe.workspaceId(), probe.hourStart(), 0, 0, 0, 0, 0, 0, -1, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HourlyRollup(probe.workspaceId(), probe.hourStart(), 0, 0, 0, 0, 0, 0, 0, -1, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HourlyRollup(probe.workspaceId(), probe.hourStart(), 0, 0, 0, 0, 0, 0, 0, 0, -1));
  }
}
