/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.dto.CreateSnapshotRequest;
import dev.jahaziel.api.dto.SnapshotDto;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** User-facing snapshot endpoints. Manual snapshots count against quota. */
@RestController
@RequestMapping("/api/v1")
public class SnapshotController {
  private final SnapshotService snapshots;
  private final WorkspaceService workspaces;
  private final AuthorizationService auth;
  private final String appDomain;

  /**
   * Creates the controller.
   *
   * @param snapshots the snapshot service
   * @param workspaces the workspace lookup for restore responses
   * @param auth the authorization checks
   * @param appDomain the public domain
   */
  public SnapshotController(
      SnapshotService snapshots,
      WorkspaceService workspaces,
      AuthorizationService auth,
      @Value("${jahaziel.domain}") String appDomain) {
    this.snapshots = snapshots;
    this.workspaces = workspaces;
    this.auth = auth;
    this.appDomain = appDomain;
  }

  /**
   * Lists a workspace's snapshots newest first.
   *
   * @param id the workspace identifier
   * @param principal the acting user
   * @return the snapshot DTOs
   */
  @GetMapping("/workspaces/{id}/snapshots")
  public List<SnapshotDto> list(@PathVariable UUID id, Principal principal) {
    WorkspaceId workspace = require(principal, id, "read");
    return snapshots.list(workspace).stream().map(SnapshotDto::from).toList();
  }

  /**
   * Creates a manual snapshot.
   *
   * @param id the workspace identifier
   * @param request the tag
   * @param principal the acting user
   * @return the snapshot DTO
   */
  @PostMapping("/workspaces/{id}/snapshots")
  @ResponseStatus(HttpStatus.CREATED)
  public SnapshotDto create(
      @PathVariable UUID id,
      @Valid @RequestBody CreateSnapshotRequest request,
      Principal principal) {
    WorkspaceId workspace = require(principal, id, "write");
    return SnapshotDto.from(
        snapshots.create(workspace, request.tag(), SnapshotKind.MANUAL, principal.getName()));
  }

  /**
   * Restores a workspace home from a snapshot.
   *
   * @param id the workspace identifier
   * @param snapshotId the snapshot identifier
   * @param principal the acting user
   * @return the workspace DTO
   */
  @PostMapping("/workspaces/{id}/snapshots/{snapshotId}/restore")
  public WorkspaceDto restore(
      @PathVariable UUID id, @PathVariable UUID snapshotId, Principal principal) {
    WorkspaceId workspace = require(principal, id, "write");
    snapshots.restore(workspace, new SnapshotId(snapshotId));
    return WorkspaceDto.from(workspaces.describe(workspace), appDomain);
  }

  private WorkspaceId require(Principal principal, UUID id, String permission) {
    UserId user;
    try {
      user = new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
    WorkspaceId workspace = new WorkspaceId(id);
    if (!auth.isAllowed(user, permission, workspace)) {
      throw new AccessDeniedException(user, permission);
    }
    return workspace;
  }
}
