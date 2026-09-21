/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.workspace.api.WorkspaceView;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Workspace view for reads.
 *
 * @param workspaceId the workspace identifier
 * @param name the workspace name
 * @param state the lowercase lifecycle state
 * @param createdAt when the workspace was created
 * @param lastActivityAt last state change
 * @param quota the quota view
 * @param ports the registered ports, each with its tokenized public URL
 */
public record WorkspaceDto(
    UUID workspaceId,
    String name,
    String state,
    Instant createdAt,
    Instant lastActivityAt,
    WorkspaceQuotaDto quota,
    List<PortDto> ports) {
  /**
   * Maps a view to a DTO.
   *
   * @param view the view
   * @param ports the workspace's ports
   * @return the DTO
   */
  public static WorkspaceDto from(WorkspaceView view, List<PortDto> ports) {
    var workspace = view.workspace();
    var quotaDto =
        new WorkspaceQuotaDto(
            0, workspace.quota().logicalBytes(), 0, workspace.quota().inodeLimit());
    return new WorkspaceDto(
        workspace.id().value(),
        workspace.name(),
        workspace.state().getClass().getSimpleName().toLowerCase(Locale.ROOT),
        view.createdAt(),
        view.lastActivityAt(),
        quotaDto,
        List.copyOf(ports));
  }
}
