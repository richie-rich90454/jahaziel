/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.controller;

import dev.jahaziel.api.dto.PortDto;
import dev.jahaziel.api.dto.RegisterPortRequest;
import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.GatewayRouteService;
import dev.jahaziel.networking.api.PortBinding;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Duration;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing port endpoints. A registered port becomes reachable at its host-form URL; that URL
 * carries a workspace-scoped session token, which the gateway verifies before routing.
 */
@RestController
@RequestMapping("/api/v1")
public class PortController {
  private final PortService ports;
  private final UserService users;
  private final AuthorizationService auth;
  private final GatewayRouteService routes;
  private final JwtService tokens;
  private final String appDomain;
  private final long tokenTtlSeconds;

  /**
   * Creates the controller.
   *
   * @param ports the port service
   * @param users the user lookup for URL slugs
   * @param auth the authorization checks
   * @param routes the gateway route reconciler
   * @param tokens the session token issuer
   * @param appDomain the public domain
   * @param tokenTtlSeconds the gateway token lifetime
   */
  public PortController(
      PortService ports,
      UserService users,
      AuthorizationService auth,
      GatewayRouteService routes,
      JwtService tokens,
      @Value("${jahaziel.domain}") String appDomain,
      @Value("${jahaziel.jwt.ttl-seconds:900}") long tokenTtlSeconds) {
    this.ports = ports;
    this.users = users;
    this.auth = auth;
    this.routes = routes;
    this.tokens = tokens;
    this.appDomain = appDomain;
    this.tokenTtlSeconds = tokenTtlSeconds;
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
    UserId user = userId(principal);
    WorkspaceId workspace = new WorkspaceId(id);
    require(user, workspace, "read");
    return describe(user, workspace);
  }

  /**
   * Registers a workspace port and returns its ready-to-use URL.
   *
   * @param id the workspace identifier
   * @param request the port to register
   * @param principal the acting user
   * @return the port DTO with a tokenized URL
   */
  @PostMapping("/workspaces/{id}/ports")
  @ResponseStatus(HttpStatus.CREATED)
  public PortDto register(
      @PathVariable UUID id, @Valid @RequestBody RegisterPortRequest request, Principal principal) {
    UserId user = userId(principal);
    WorkspaceId workspace = new WorkspaceId(id);
    require(user, workspace, "write");
    PortBinding binding = ports.registerPort(user, request.port(), workspace, request.protocol());
    routes.reconcile();
    return PortDto.from(
        binding, users.slug(user), appDomain, token(user, workspace, binding.port()));
  }

  /**
   * Removes a workspace port.
   *
   * @param id the workspace identifier
   * @param port the port to remove
   * @param principal the acting user
   */
  @DeleteMapping("/workspaces/{id}/ports/{port}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unregister(@PathVariable UUID id, @PathVariable int port, Principal principal) {
    UserId user = userId(principal);
    WorkspaceId workspace = new WorkspaceId(id);
    require(user, workspace, "write");
    ports.unregisterPort(user, port);
    routes.reconcile();
  }

  private List<PortDto> describe(UserId user, WorkspaceId workspace) {
    return ports.bindingsByWorkspace(workspace).stream()
        .map(
            binding -> {
              // The slug belongs to the port's owner, not to a grantee reading the workspace.
              UserId portOwner = new UserId(binding.userId());
              return PortDto.from(
                  binding,
                  users.slug(portOwner),
                  appDomain,
                  token(portOwner, workspace, binding.port()));
            })
        .toList();
  }

  private String token(UserId user, WorkspaceId workspace, int port) {
    return tokens.issue(
        user,
        workspace.value(),
        List.of("workspace:port:" + port),
        Duration.ofSeconds(tokenTtlSeconds));
  }

  private void require(UserId user, WorkspaceId workspace, String permission) {
    if (!auth.isAllowed(user, permission, workspace)) {
      throw new AccessDeniedException(user, permission);
    }
  }

  private static UserId userId(Principal principal) {
    try {
      return new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }
}
