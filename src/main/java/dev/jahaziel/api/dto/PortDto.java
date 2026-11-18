/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.networking.api.GatewayEndpoint;
import dev.jahaziel.networking.api.PortBinding;
import java.time.Instant;
import java.util.Objects;

/**
 * A registered port for reads.
 *
 * @param port the bound port
 * @param protocol the transport protocol
 * @param url the public URL, carrying a workspace-scoped session token; null when none is minted
 * @param registeredAt when the binding was registered
 * @param listening whether a TCP connection currently succeeds on the loopback port, or null when
 *     the port was not probed for this read
 */
public record PortDto(
    int port, String protocol, String url, Instant registeredAt, Boolean listening) {
  /**
   * Maps a binding to a DTO.
   *
   * @param binding the binding
   * @param userSlug the URL-safe user slug
   * @param endpoint the public gateway address
   * @param token the workspace-scoped token, or null to omit the URL
   * @param listening whether the registered port currently accepts connections
   * @return the DTO
   */
  public static PortDto from(
      PortBinding binding,
      String userSlug,
      GatewayEndpoint endpoint,
      String token,
      Boolean listening) {
    Objects.requireNonNull(userSlug, "userSlug");
    String url =
        token == null ? null : endpoint.previewUrl(binding.workspaceId(), binding.port(), token);
    return new PortDto(binding.port(), binding.protocol(), url, binding.registeredAt(), listening);
  }

  /** Maps a binding when no local listener check was requested. */
  public static PortDto from(
      PortBinding binding, String userSlug, GatewayEndpoint endpoint, String token) {
    return from(binding, userSlug, endpoint, token, null);
  }
}
