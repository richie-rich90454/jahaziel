/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.api.dto.PortDto;
import dev.jahaziel.api.dto.WorkspaceDto;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.networking.api.GatewayEndpoint;
import dev.jahaziel.networking.api.PortService;
import dev.jahaziel.workspace.api.WorkspaceView;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Builds workspace read models with their entry points. A user's own read carries a fresh
 * workspace-scoped session token per port; an administration read never does, because a token is
 * minted for one acting user and one workspace.
 */
public final class WorkspaceDtoFactory {
  private final PortService ports;
  private final UserService users;
  private final JwtService tokens;
  private final GatewayEndpoint endpoint;
  private final long tokenTtlSeconds;

  /**
   * Creates the factory.
   *
   * @param ports the port bindings
   * @param users the user lookup, for URL slugs
   * @param tokens the session token issuer
   * @param endpoint the public gateway address
   * @param tokenTtlSeconds the gateway token lifetime
   */
  public WorkspaceDtoFactory(
      PortService ports,
      UserService users,
      JwtService tokens,
      GatewayEndpoint endpoint,
      long tokenTtlSeconds) {
    this.ports = Objects.requireNonNull(ports, "ports");
    this.users = Objects.requireNonNull(users, "users");
    this.tokens = Objects.requireNonNull(tokens, "tokens");
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    this.tokenTtlSeconds = tokenTtlSeconds;
  }

  /**
   * Builds the view for its owner, with a tokenized URL per port.
   *
   * @param owner the acting user
   * @param view the workspace view
   * @return the DTO
   */
  public WorkspaceDto forUser(UserId owner, WorkspaceView view) {
    String slug = users.slug(owner);
    List<PortDto> entries =
        ports.bindingsByWorkspace(view.workspace().id()).stream()
            .map(
                binding ->
                    PortDto.from(binding, slug, endpoint, token(owner, view, binding.port())))
            .toList();
    return WorkspaceDto.from(view, entries);
  }

  /**
   * Builds the view for administration, without tokens.
   *
   * @param view the workspace view
   * @return the DTO
   */
  public WorkspaceDto withoutTokens(WorkspaceView view) {
    String slug = users.slug(view.workspace().owner());
    List<PortDto> entries =
        ports.bindingsByWorkspace(view.workspace().id()).stream()
            .map(binding -> PortDto.from(binding, slug, endpoint, null))
            .toList();
    return WorkspaceDto.from(view, entries);
  }

  private String token(UserId owner, WorkspaceView view, int port) {
    return tokens.issue(
        owner,
        view.workspace().id().value(),
        List.of("workspace:port:" + port),
        Duration.ofSeconds(tokenTtlSeconds));
  }
}
