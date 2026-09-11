/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.dto.CreateWorkspaceRequest;
import dev.jahaziel.api.dto.UserDto;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
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
  private final UserService users;
  private final String appDomain;
  private final String defaultNode;

  /**
   * Creates the controller.
   *
   * @param workspaces the workspace service
   * @param users the user lookup
   * @param appDomain the public domain
   * @param defaultNode the single node id until the scheduler lands
   */
  public WorkspaceController(
      WorkspaceService workspaces,
      UserService users,
      @Value("${jahaziel.domain}") String appDomain,
      @Value("${jahaziel.default-node:node-a}") String defaultNode) {
    this.workspaces = workspaces;
    this.users = users;
    this.appDomain = appDomain;
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
    return workspaces.describeByOwner(userId(principal)).stream()
        .map(view -> WorkspaceDto.from(view, appDomain))
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
    return WorkspaceDto.from(workspaces.describe(workspace.id()), appDomain);
  }

  /**
   * Returns one workspace.
   *
   * @param id the workspace identifier
   * @return the workspace DTO
   */
  @GetMapping("/workspaces/{id}")
  public WorkspaceDto get(@PathVariable UUID id) {
    return WorkspaceDto.from(workspaces.describe(new WorkspaceId(id)), appDomain);
  }

  /**
   * Deletes a workspace.
   *
   * @param id the workspace identifier
   * @param confirm must be true
   */
  @DeleteMapping("/workspaces/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean confirm) {
    if (!confirm) {
      throw new IllegalArgumentException("deletion requires confirm=true");
    }
    workspaces.delete(new WorkspaceId(id));
  }

  private static UserId userId(Principal principal) {
    try {
      return new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }
}
