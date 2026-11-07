/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.api.WorkspaceGitService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated Git status, diff, staging, and commit operations for one workspace. */
@RestController
@RequestMapping("/api/v1/workspaces/{id}/git")
public final class WorkspaceGitController {
  private final WorkspaceGitService git;
  private final AuthorizationService authorization;

  public WorkspaceGitController(WorkspaceGitService git, AuthorizationService authorization) {
    this.git = git;
    this.authorization = authorization;
  }

  @GetMapping
  public WorkspaceGitService.Status status(@PathVariable UUID id, Principal principal) {
    return git.status(require(principal, id, "read"));
  }

  @GetMapping("/diff")
  public WorkspaceGitService.Diff diff(
      @PathVariable UUID id, @RequestParam String path, Principal principal) {
    return git.diff(require(principal, id, "read"), path);
  }

  @PostMapping("/stage")
  public WorkspaceGitService.Status stage(
      @PathVariable UUID id, @Valid @RequestBody PathRequest request, Principal principal) {
    return git.stage(require(principal, id, "write"), request.path());
  }

  @PostMapping("/unstage")
  public WorkspaceGitService.Status unstage(
      @PathVariable UUID id, @Valid @RequestBody PathRequest request, Principal principal) {
    return git.unstage(require(principal, id, "write"), request.path());
  }

  @PostMapping("/commit")
  public WorkspaceGitService.CommitResult commit(
      @PathVariable UUID id, @Valid @RequestBody CommitRequest request, Principal principal) {
    return git.commit(require(principal, id, "write"), request.message());
  }

  private WorkspaceId require(Principal principal, UUID id, String permission) {
    UserId user;
    try {
      user = new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
    WorkspaceId workspace = new WorkspaceId(id);
    if (!authorization.isAllowed(user, permission, workspace)) {
      throw new AccessDeniedException(user, permission);
    }
    return workspace;
  }

  public record PathRequest(@NotBlank @Size(max = 1024) String path) {}

  public record CommitRequest(@NotBlank @Size(max = 512) String message) {}
}
