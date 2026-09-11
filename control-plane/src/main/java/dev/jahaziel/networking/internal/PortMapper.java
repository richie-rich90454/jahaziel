/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.networking.persistence.WorkspacePortEntity;
import java.time.Instant;

/** Maps port rows to bindings and back. Pure static functions. */
public final class PortMapper {
  private PortMapper() {}

  /**
   * Maps a binding to its row.
   *
   * @param binding the binding
   * @return the row
   */
  public static WorkspacePortEntity toEntity(PortBinding binding) {
    WorkspacePortEntity row = new WorkspacePortEntity();
    WorkspacePortEntity.Id id = new WorkspacePortEntity.Id();
    id.setWorkspaceId(binding.workspaceId());
    id.setPort(binding.port());
    row.setId(id);
    row.setProtocol(binding.protocol());
    row.setRegisteredAt(binding.registeredAt());
    row.setLastSeenAt(Instant.now());
    return row;
  }

  /**
   * Rebuilds a binding from its row. The owner is not stored per binding; callers supply it.
   *
   * @param row the row
   * @param userId the owning user identifier
   * @return the binding
   */
  public static PortBinding toDomain(WorkspacePortEntity row, java.util.UUID userId) {
    return new PortBinding(
        userId,
        row.getId().getPort(),
        row.getId().getWorkspaceId(),
        row.getProtocol(),
        row.getRegisteredAt());
  }
}
