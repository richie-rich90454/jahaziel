/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.monitoring.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coverage of {@link ResourceMetricEntity} and its composite key. */
class ResourceMetricEntityTest {
  @Test
  void storesAllFields() {
    ResourceMetricEntity entity = new ResourceMetricEntity();
    ResourceMetricEntity.Id id = new ResourceMetricEntity.Id(UUID.randomUUID(), Instant.EPOCH);
    entity.setId(id);
    entity.setCpuAvgPercent(1.5f);
    entity.setCpuMaxPercent(9.5f);
    entity.setMemoryAvgMib(100L);
    entity.setMemoryMaxMib(200L);
    entity.setDiskReadAvgBps(3L);
    entity.setDiskWriteAvgBps(4L);
    entity.setNetworkRxAvgBps(5L);
    entity.setNetworkTxAvgBps(6L);
    entity.setProcessMax(7);
    assertEquals(id, entity.getId());
    assertEquals(1.5f, entity.getCpuAvgPercent(), 0.0001f);
    assertEquals(9.5f, entity.getCpuMaxPercent(), 0.0001f);
    assertEquals(100L, entity.getMemoryAvgMib());
    assertEquals(200L, entity.getMemoryMaxMib());
    assertEquals(3L, entity.getDiskReadAvgBps());
    assertEquals(4L, entity.getDiskWriteAvgBps());
    assertEquals(5L, entity.getNetworkRxAvgBps());
    assertEquals(6L, entity.getNetworkTxAvgBps());
    assertEquals(7, entity.getProcessMax());
  }

  @Test
  void equalsById() {
    ResourceMetricEntity.Id id = new ResourceMetricEntity.Id(UUID.randomUUID(), Instant.EPOCH);
    ResourceMetricEntity first = new ResourceMetricEntity();
    first.setId(id);
    first.setProcessMax(1);
    ResourceMetricEntity second = new ResourceMetricEntity();
    second.setId(id);
    second.setProcessMax(2);
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setId(new ResourceMetricEntity.Id(UUID.randomUUID(), Instant.EPOCH));
    assertFalse(first.equals(second));
  }

  @Test
  void idStoresFieldsAndCompares() {
    UUID workspaceId = UUID.randomUUID();
    ResourceMetricEntity.Id id = new ResourceMetricEntity.Id();
    id.setWorkspaceId(workspaceId);
    id.setHourStart(Instant.EPOCH);
    assertEquals(workspaceId, id.getWorkspaceId());
    assertEquals(Instant.EPOCH, id.getHourStart());
    ResourceMetricEntity.Id same = new ResourceMetricEntity.Id(workspaceId, Instant.EPOCH);
    assertTrue(id.equals(id));
    assertTrue(id.equals(same));
    assertEquals(id.hashCode(), same.hashCode());
    assertFalse(id.equals(null));
    assertFalse(id.equals((Object) "other"));
    assertFalse(id.equals(new ResourceMetricEntity.Id(UUID.randomUUID(), Instant.EPOCH)));
    assertFalse(id.equals(new ResourceMetricEntity.Id(workspaceId, Instant.now())));
  }
}
