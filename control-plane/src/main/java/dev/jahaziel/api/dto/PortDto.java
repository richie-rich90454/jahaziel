/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api.dto;

import dev.jahaziel.networking.api.PortBinding;
import java.time.Instant;

/**
 * A registered port for reads.
 *
 * @param port the bound port
 * @param protocol the transport protocol
 * @param url the public URL, carrying a workspace-scoped session token; null when none is minted
 * @param registeredAt when the binding was registered
 */
public record PortDto(int port, String protocol, String url, Instant registeredAt) {
  /**
   * Maps a binding to a DTO.
   *
   * @param binding the binding
   * @param userSlug the URL-safe user slug
   * @param appDomain the public domain
   * @param token the workspace-scoped token, or null to omit the URL
   * @return the DTO
   */
  public static PortDto from(PortBinding binding, String userSlug, String appDomain, String token) {
    String url =
        token == null
            ? null
            : "https://" + userSlug + "-" + binding.port() + "." + appDomain + "/?token=" + token;
    return new PortDto(binding.port(), binding.protocol(), url, binding.registeredAt());
  }
}
