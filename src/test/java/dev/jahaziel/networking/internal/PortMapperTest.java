/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.networking.persistence.WorkspacePortEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior of {@link PortMapper} with stubbed rows. */
class PortMapperTest {
  private final UUID user = UUID.randomUUID();
  private final UUID workspace = UUID.randomUUID();
  private final Instant at = Instant.parse("2026-01-02T03:04:05Z");

  @Test
  void toEntityMapsBinding() {
    PortBinding binding = new PortBinding(user, 8080, workspace, "http", at);
    WorkspacePortEntity row = PortMapper.toEntity(binding);
    assertEquals(workspace, row.getId().getWorkspaceId());
    assertEquals(8080, row.getId().getPort());
    assertEquals("http", row.getProtocol());
    assertEquals(at, row.getRegisteredAt());
  }

  @Test
  void toDomainRestoresBinding() {
    PortBinding binding = PortMapper.toDomain(mockRow(), user);
    assertEquals(workspace, binding.workspaceId());
    assertEquals(user, binding.userId());
    assertEquals(8080, binding.port());
  }

  @Test
  void badPortsAndProtocolsAreRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> new PortBinding(user, 0, workspace, "http", at));
    assertThrows(
        IllegalArgumentException.class, () -> new PortBinding(user, 8080, workspace, "ftp", at));
    assertThrows(
        IllegalArgumentException.class, () -> new PortBinding(user, 70000, workspace, "http", at));
  }

  private WorkspacePortEntity mockRow() {
    WorkspacePortEntity row = mock(WorkspacePortEntity.class);
    WorkspacePortEntity.Id key = mock(WorkspacePortEntity.Id.class);
    when(row.getId()).thenReturn(key);
    when(key.getWorkspaceId()).thenReturn(workspace);
    when(key.getPort()).thenReturn(8080);
    when(row.getProtocol()).thenReturn("http");
    when(row.getRegisteredAt()).thenReturn(at);
    return row;
  }
}
