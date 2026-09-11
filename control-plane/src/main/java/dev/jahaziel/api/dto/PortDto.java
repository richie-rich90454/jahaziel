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
 * @param url the public URL
 * @param registeredAt when the binding was registered
 */
public record PortDto(int port, String protocol, String url, Instant registeredAt) {
  /**
   * Maps a binding to a DTO.
   *
   * @param binding the binding
   * @param userSlug the URL-safe user slug
   * @param appDomain the public domain
   * @return the DTO
   */
  public static PortDto from(PortBinding binding, String userSlug, String appDomain) {
    return new PortDto(
        binding.port(),
        binding.protocol(),
        "https://" + userSlug + "-" + binding.port() + "." + appDomain,
        binding.registeredAt());
  }
}
