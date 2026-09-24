/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coverage of {@link WorkspacePortEntity} and its composite key. */
class WorkspacePortEntityTest {
  @Test
  void storesAllFields() {
    WorkspacePortEntity entity = new WorkspacePortEntity();
    WorkspacePortEntity.Id id = new WorkspacePortEntity.Id(UUID.randomUUID(), 8080);
    entity.setId(id);
    entity.setProtocol("http");
    entity.setRegisteredAt(Instant.EPOCH);
    entity.setLastSeenAt(Instant.now());
    assertEquals(id, entity.getId());
    assertEquals("http", entity.getProtocol());
    assertEquals(Instant.EPOCH, entity.getRegisteredAt());
  }

  @Test
  void equalsById() {
    WorkspacePortEntity.Id id = new WorkspacePortEntity.Id(UUID.randomUUID(), 8080);
    WorkspacePortEntity first = new WorkspacePortEntity();
    first.setId(id);
    first.setProtocol("http");
    WorkspacePortEntity second = new WorkspacePortEntity();
    second.setId(id);
    second.setProtocol("tcp");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setId(new WorkspacePortEntity.Id(id.getWorkspaceId(), 9090));
    assertFalse(first.equals(second));
  }

  @Test
  void idStoresFieldsAndCompares() {
    UUID workspaceId = UUID.randomUUID();
    WorkspacePortEntity.Id id = new WorkspacePortEntity.Id();
    id.setWorkspaceId(workspaceId);
    id.setPort(8080);
    assertEquals(workspaceId, id.getWorkspaceId());
    assertEquals(8080, id.getPort());
    WorkspacePortEntity.Id same = new WorkspacePortEntity.Id(workspaceId, 8080);
    assertTrue(id.equals(id));
    assertTrue(id.equals(same));
    assertEquals(id.hashCode(), same.hashCode());
    assertFalse(id.equals(null));
    assertFalse(id.equals((Object) "other"));
    assertFalse(id.equals(new WorkspacePortEntity.Id(workspaceId, 9090)));
    assertFalse(id.equals(new WorkspacePortEntity.Id(UUID.randomUUID(), 8080)));
  }
}
