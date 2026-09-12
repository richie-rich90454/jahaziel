/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.monitoring.api.HourlyRollup;
import dev.jahaziel.monitoring.persistence.ResourceMetricEntity;
import dev.jahaziel.monitoring.persistence.ResourceMetricRepository;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Behavior of {@link MetricsServiceImpl} with stubbed rows. */
class MetricsServiceImplTest {
  private ResourceMetricRepository metrics;
  private MetricsServiceImpl service;
  private UUID workspace;

  @BeforeEach
  void setUp() {
    metrics = mock(ResourceMetricRepository.class);
    service = new MetricsServiceImpl(metrics);
    workspace = UUID.randomUUID();
  }

  @Test
  void recordsRollup() {
    HourlyRollup rollup = rollup();
    service.record(rollup);
    ArgumentCaptor<ResourceMetricEntity> captor =
        ArgumentCaptor.forClass(ResourceMetricEntity.class);
    verify(metrics).save(captor.capture());
    assertEquals(workspace, captor.getValue().getId().getWorkspaceId());
  }

  @Test
  void rejectsNullRollup() {
    assertThrows(NullPointerException.class, () -> service.record(null));
  }

  @Test
  void latestMapsNewestRow() {
    ResourceMetricEntity row = mock(ResourceMetricEntity.class);
    ResourceMetricEntity.Id key = mock(ResourceMetricEntity.Id.class);
    when(row.getId()).thenReturn(key);
    when(key.getWorkspaceId()).thenReturn(workspace);
    when(key.getHourStart()).thenReturn(Instant.parse("2026-01-02T03:00:00Z"));
    when(metrics.findLatest(any(UUID.class), any())).thenReturn(List.of(row));
    var found = service.latest(new WorkspaceId(workspace));
    assertTrue(found.isPresent());
    assertEquals(workspace, found.get().workspaceId());
  }

  @Test
  void latestEmptyWhenNone() {
    when(metrics.findLatest(any(UUID.class), any())).thenReturn(List.of());
    assertTrue(service.latest(new WorkspaceId(workspace)).isEmpty());
  }

  private HourlyRollup rollup() {
    return new HourlyRollup(
        workspace, Instant.parse("2026-01-02T03:00:00Z"), 41, 90, 1024, 2048, 1000, 2000, 3000,
        4000, 81);
  }
}
