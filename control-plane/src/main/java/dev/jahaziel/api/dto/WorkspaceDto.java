/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.workspace.api.WorkspaceView;
import java.time.Instant;
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
 * @param urls the entry points
 */
public record WorkspaceDto(
    UUID workspaceId,
    String name,
    String state,
    Instant createdAt,
    Instant lastActivityAt,
    WorkspaceQuotaDto quota,
    WorkspaceUrlsDto urls) {
  /**
   * Maps a view to a DTO.
   *
   * @param view the view
   * @param appDomain the public domain, for example {@code code.jahaziel.dev}
   * @return the DTO
   */
  public static WorkspaceDto from(WorkspaceView view, String appDomain) {
    var workspace = view.workspace();
    var quotaDto =
        new WorkspaceQuotaDto(
            0, workspace.quota().logicalBytes(), 0, workspace.quota().inodeLimit());
    var urls =
        new WorkspaceUrlsDto(
            "wss://" + appDomain + "/ws/ide/" + workspace.id().value(),
            "wss://" + appDomain + "/ws/terminal/" + workspace.id().value());
    return new WorkspaceDto(
        workspace.id().value(),
        workspace.name(),
        workspace.state().getClass().getSimpleName().toLowerCase(Locale.ROOT),
        view.createdAt(),
        view.lastActivityAt(),
        quotaDto,
        urls);
  }
}
