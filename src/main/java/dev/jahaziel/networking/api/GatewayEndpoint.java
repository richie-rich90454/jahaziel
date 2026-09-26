/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.api;

import java.util.Objects;

/**
 * The public gateway address behind which workspace ports are served. The gateway itself speaks
 * plain HTTP; anything terminating TLS in front of it configures {@code https} here. Port zero is
 * the ephemeral convention used by tests: the OS allocates the port at bind time.
 *
 * @param scheme {@code http} or {@code https}
 * @param domain the public domain the gateway routes, for example {@code code.example.com}
 * @param port the public gateway port, or zero for ephemeral
 */
public record GatewayEndpoint(String scheme, String domain, int port) {
  public GatewayEndpoint {
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new IllegalArgumentException("scheme must be http or https");
    }
    Objects.requireNonNull(domain, "domain");
    if (domain.isBlank()) {
      throw new IllegalArgumentException("domain must not be blank");
    }
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port must be between 0 and 65535");
    }
  }

  /**
   * Builds the tokenized URL of a workspace port.
   *
   * @param userSlug the URL-safe owner slug
   * @param workspacePort the bound workspace port
   * @param token the workspace-scoped session token
   * @return the public URL
   */
  public String url(String userSlug, int workspacePort, String token) {
    Objects.requireNonNull(userSlug, "userSlug");
    Objects.requireNonNull(token, "token");
    String authority = userSlug + "-" + workspacePort + "." + domain + portSuffix();
    return scheme + "://" + authority + "/?token=" + token;
  }

  private String portSuffix() {
    if (scheme.equals("http") && port == 80 || scheme.equals("https") && port == 443) {
      return "";
    }
    return ":" + port;
  }
}
