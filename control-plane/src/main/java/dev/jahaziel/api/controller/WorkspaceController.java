/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.WorkspaceDtoFactory;
import dev.jahaziel.api.dto.CreateWorkspaceRequest;
import dev.jahaziel.api.dto.UserDto;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.runtime.api.NodeAgentUnreachableException;
import dev.jahaziel.runtime.api.RuntimeService;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** User-facing workspace endpoints. The principal name carries the user id until OIDC lands. */
@RestController
@RequestMapping("/api/v1")
public class WorkspaceController {
  private final WorkspaceService workspaces;
  private final RuntimeService runtime;
  private final AuthorizationService auth;
  private final UserService users;
  private final WorkspaceDtoFactory dtos;
  private final String defaultNode;

  /**
   * Creates the controller.
   *
   * @param workspaces the workspace service
   * @param runtime the runtime lifecycle for stop-before-delete
   * @param auth the authorization checks
   * @param users the user lookup
   * @param dtos the workspace read-model factory
   * @param defaultNode the single node id until the scheduler lands
   */
  public WorkspaceController(
      WorkspaceService workspaces,
      RuntimeService runtime,
      AuthorizationService auth,
      UserService users,
      WorkspaceDtoFactory dtos,
      @Value("${jahaziel.default-node:node-a}") String defaultNode) {
    this.workspaces = workspaces;
    this.runtime = runtime;
    this.auth = auth;
    this.users = users;
    this.dtos = dtos;
    this.defaultNode = defaultNode;
  }

  /**
   * Returns the current user.
   *
   * @param principal the authenticated principal
   * @return the user DTO
   */
  @GetMapping("/me")
  public UserDto me(Principal principal) {
    UserId id = userId(principal);
    var identity = users.get(id);
    return new UserDto(
        id.value(),
        identity.email(),
        identity.displayName(),
        identity.roles(),
        users.isMfaEnabled(id));
  }

  /**
   * Lists the current user's workspaces.
   *
   * @param principal the authenticated principal
   * @return the workspace DTOs
   */
  @GetMapping("/me/workspaces")
  public List<WorkspaceDto> listMine(Principal principal) {
    UserId owner = userId(principal);
    return workspaces.describeByOwner(owner).stream()
        .map(view -> dtos.forUser(owner, view))
        .toList();
  }

  /**
   * Creates a workspace.
   *
   * @param principal the authenticated principal
   * @param request the create request
   * @return the workspace DTO
   */
  @PostMapping("/workspaces")
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceDto create(
      Principal principal, @Valid @RequestBody CreateWorkspaceRequest request) {
    int vcpu = request.vcpuCount() == null ? 2 : request.vcpuCount();
    long memory = request.memoryMib() == null ? 2048 : request.memoryMib();
    var workspace =
        workspaces.create(userId(principal), request.name(), vcpu, memory, new NodeId(defaultNode));
    return dtos.forUser(userId(principal), workspaces.describe(workspace.id()));
  }

  /**
   * Returns one workspace.
   *
   * @param id the workspace identifier
   * @param principal the acting user
   * @return the workspace DTO
   */
  @GetMapping("/workspaces/{id}")
  public WorkspaceDto get(@PathVariable UUID id, Principal principal) {
    UserId owner = userId(principal);
    return dtos.forUser(owner, workspaces.describe(require(principal, id, "read")));
  }

  /**
   * Deletes a workspace.
   *
   * @param id the workspace identifier
   * @param confirm must be true
   * @param principal the acting user
   */
  @DeleteMapping("/workspaces/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "false") boolean confirm,
      Principal principal) {
    if (!confirm) {
      throw new IllegalArgumentException("deletion requires confirm=true");
    }
    WorkspaceId workspace = require(principal, id, "admin");
    try {
      runtime.stop(workspace);
    } catch (NodeAgentUnreachableException unreachable) {
      // Fail-closed backend: nothing reachable to stop; still release the record.
    }
    workspaces.delete(workspace);
  }

  private WorkspaceId require(Principal principal, UUID id, String permission) {
    UserId user = userId(principal);
    WorkspaceId workspace = new WorkspaceId(id);
    if (!auth.isAllowed(user, permission, workspace)) {
      throw new AccessDeniedException(user, permission);
    }
    return workspace;
  }

  /** Builds the workspace's entry points, each carrying a fresh workspace-scoped token. */
  private static UserId userId(Principal principal) {
    try {
      return new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }
}
