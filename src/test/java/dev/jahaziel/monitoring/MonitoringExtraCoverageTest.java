/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.api.dto.MetricsDto;
import dev.jahaziel.monitoring.api.HourlyRollup;
import dev.jahaziel.monitoring.internal.MetricsMapper;
import dev.jahaziel.monitoring.internal.MetricsServiceImpl;
import dev.jahaziel.monitoring.internal.ResourceSamplingJob;
import dev.jahaziel.monitoring.persistence.ResourceMetricEntity;
import dev.jahaziel.monitoring.persistence.ResourceMetricRepository;
import dev.jahaziel.spi.JobContext;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class MonitoringExtraCoverageTest {
  @Test
  void hourlyRollupGuards() {
    UUID w = UUID.randomUUID();
    Instant t = Instant.now();
    assertThrows(
        NullPointerException.class, () -> new HourlyRollup(null, t, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    assertThrows(
        NullPointerException.class, () -> new HourlyRollup(w, null, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class, () -> new HourlyRollup(w, t, -1, 0, 0, 0, 0, 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class, () -> new HourlyRollup(w, t, 0, -1, 0, 0, 0, 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class, () -> new HourlyRollup(w, t, 0, 0, -1, 0, 0, 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class, () -> new HourlyRollup(w, t, 0, 0, 0, -1, 0, 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class, () -> new HourlyRollup(w, t, 0, 0, 0, 0, -1, 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class, () -> new HourlyRollup(w, t, 0, 0, 0, 0, 0, -1, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class, () -> new HourlyRollup(w, t, 0, 0, 0, 0, 0, 0, -1, 0, 0));
    assertThrows(
        IllegalArgumentException.class, () -> new HourlyRollup(w, t, 0, 0, 0, 0, 0, 0, 0, -1, 0));
    assertThrows(
        IllegalArgumentException.class, () -> new HourlyRollup(w, t, 0, 0, 0, 0, 0, 0, 0, 0, -1));
    MetricsDto dto = MetricsDto.from(new HourlyRollup(w, t, 1.5f, 2f, 1, 2, 3, 4, 5, 6, 7));
    assertEquals(1L * 1024 * 1024, dto.memoryUsedBytes());
    assertEquals(2L * 1024 * 1024, dto.memoryPeakBytes());
    assertEquals(MetricsDto.zero(), MetricsDto.zero());
  }

  @Test
  void metricsMapperRoundTrip() {
    UUID w = UUID.randomUUID();
    Instant t = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    HourlyRollup rollup = new HourlyRollup(w, t, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    ResourceMetricEntity row = MetricsMapper.toEntity(rollup);
    assertEquals(rollup, MetricsMapper.toDomain(row));
  }

  @Test
  void metricsServiceLatestEmptyAndPresent() {
    ResourceMetricRepository repo = mock(ResourceMetricRepository.class);
    MetricsServiceImpl svc = new MetricsServiceImpl(repo);
    WorkspaceId id = new WorkspaceId(UUID.randomUUID());
    when(repo.findLatest(any(), any())).thenReturn(List.of());
    assertTrue(svc.latest(id).isEmpty());
    ResourceMetricEntity row =
        MetricsMapper.toEntity(
            new HourlyRollup(id.value(), Instant.now(), 0, 0, 0, 0, 0, 0, 0, 0, 0));
    when(repo.findLatest(any(), any())).thenReturn(List.of(row));
    assertTrue(svc.latest(id).isPresent());
    verify(repo, times(2)).findLatest(any(UUID.class), any(PageRequest.class));
    assertThrows(NullPointerException.class, () -> svc.record(null));
    assertThrows(NullPointerException.class, () -> svc.latest(null));
    assertThrows(NullPointerException.class, () -> new MetricsServiceImpl(null));
  }

  @Test
  void samplingSkipsSameHourAndCoversPeriodScope() {
    dev.jahaziel.monitoring.api.MetricsService metrics =
        mock(dev.jahaziel.monitoring.api.MetricsService.class);
    WorkspaceService workspaces = mock(WorkspaceService.class);
    when(workspaces.describeAll()).thenReturn(List.of());
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:15:00Z"), ZoneOffset.UTC);
    ResourceSamplingJob job = new ResourceSamplingJob(metrics, workspaces, clock);
    JobContext ctx = JobContext.now();
    job.run(ctx);
    job.run(ctx);
    verify(metrics, times(0)).record(any());
    assertEquals(Duration.ofSeconds(10), job.period());
    assertEquals(dev.jahaziel.spi.JobScope.CLUSTER, job.scope());
    assertThrows(
        NullPointerException.class, () -> new ResourceSamplingJob(null, workspaces, clock));
    assertThrows(NullPointerException.class, () -> new ResourceSamplingJob(metrics, null, clock));
    assertThrows(
        NullPointerException.class, () -> new ResourceSamplingJob(metrics, workspaces, null));
  }

  @Test
  void samplingRecordsPerWorkspace() {
    dev.jahaziel.monitoring.api.MetricsService metrics =
        mock(dev.jahaziel.monitoring.api.MetricsService.class);
    WorkspaceService workspaces = mock(WorkspaceService.class);
    dev.jahaziel.workspace.domain.Workspace ws =
        mock(dev.jahaziel.workspace.domain.Workspace.class);
    WorkspaceId wid = new WorkspaceId(UUID.randomUUID());
    when(ws.id()).thenReturn(wid);
    dev.jahaziel.workspace.api.WorkspaceView view =
        mock(dev.jahaziel.workspace.api.WorkspaceView.class);
    when(view.workspace()).thenReturn(ws);
    when(workspaces.describeAll()).thenReturn(List.of(view));
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:15:00Z"), ZoneOffset.UTC);
    new ResourceSamplingJob(metrics, workspaces, clock).run(JobContext.now());
    verify(metrics, times(1)).record(any(HourlyRollup.class));
  }
}
