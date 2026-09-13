/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.dto.PortDto;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User-facing port endpoints. */
@RestController
@RequestMapping("/api/v1")
public class PortController {
  private final PortService ports;
  private final UserService users;
  private final AuthorizationService auth;
  private final String appDomain;

  /**
   * Creates the controller.
   *
   * @param ports the port service
   * @param users the user lookup for URL slugs
   * @param auth the authorization checks
   * @param appDomain the public domain
   */
  public PortController(
      PortService ports,
      UserService users,
      AuthorizationService auth,
      @Value("${jahaziel.domain}") String appDomain) {
    this.ports = ports;
    this.users = users;
    this.auth = auth;
    this.appDomain = appDomain;
  }

  /**
   * Lists a workspace's registered ports.
   *
   * @param id the workspace identifier
   * @param principal the acting user
   * @return the port DTOs
   */
  @GetMapping("/workspaces/{id}/ports")
  public List<PortDto> list(@PathVariable UUID id, Principal principal) {
    UserId user;
    try {
      user = new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
    WorkspaceId workspace = new WorkspaceId(id);
    if (!auth.isAllowed(user, "read", workspace)) {
      throw new AccessDeniedException(user, "read");
    }
    var bindings = ports.bindingsByWorkspace(workspace);
    return bindings.stream()
        .map(binding -> PortDto.from(binding, slugOf(binding), appDomain))
        .toList();
  }

  private String slugOf(dev.jahaziel.networking.api.PortBinding binding) {
    return users.slug(new UserId(binding.userId()));
  }
}
