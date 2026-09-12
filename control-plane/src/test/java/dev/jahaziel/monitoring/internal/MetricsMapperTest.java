/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jahaziel.monitoring.api.HourlyRollup;
import dev.jahaziel.monitoring.persistence.ResourceMetricEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Behavior of {@link MetricsMapper} with stubbed rows. */
class MetricsMapperTest {
  private final UUID workspace = UUID.randomUUID();
  private final Instant hour = Instant.parse("2026-01-02T03:00:00Z");

  @Test
  void roundTripPreservesValues() {
    HourlyRollup rollup =
        new HourlyRollup(workspace, hour, 41, 90, 1024, 2048, 1000, 2000, 3000, 4000, 81);
    ResourceMetricEntity row = MetricsMapper.toEntity(rollup);
    assertEquals(workspace, row.getId().getWorkspaceId());
    assertEquals(hour, row.getId().getHourStart());
    HourlyRollup back = MetricsMapper.toDomain(row);
    assertEquals(rollup, back);
  }

  @Test
  void toDomainReadsRow() {
    ResourceMetricEntity row = Mockito.mock(ResourceMetricEntity.class);
    ResourceMetricEntity.Id key = Mockito.mock(ResourceMetricEntity.Id.class);
    Mockito.when(row.getId()).thenReturn(key);
    Mockito.when(key.getWorkspaceId()).thenReturn(workspace);
    Mockito.when(key.getHourStart()).thenReturn(hour);
    HourlyRollup rollup = MetricsMapper.toDomain(row);
    assertEquals(workspace, rollup.workspaceId());
    assertEquals(hour, rollup.hourStart());
  }
}
