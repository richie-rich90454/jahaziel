/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.routing;

import java.util.Objects;

/**
 * Maps wildcard hosts to users and ports. The host {@code richard-8080.code.example.com} maps to
 * user {@code richard} and port {@code 8080}: the split happens at the last dash, so slugs ending
 * in a number followed by a port stay unambiguous only by convention.
 */
public final class HostParser {
  private final String publicDomain;

  /**
   * Creates the parser.
   *
   * @param publicDomain the public domain, for example {@code code.example.com}
   */
  public HostParser(String publicDomain) {
    if (publicDomain == null || publicDomain.isBlank()) {
      throw new IllegalArgumentException("publicDomain must not be blank");
    }
    this.publicDomain = publicDomain;
  }

  /**
   * Parses one Host header value.
   *
   * @param host the Host header, without port
   * @return the user slug and port
   */
  public ParsedHost parse(String host) {
    Objects.requireNonNull(host, "host");
    String suffix = "." + publicDomain;
    if (!host.endsWith(suffix)) {
      throw new IllegalArgumentException("host does not belong to " + publicDomain + ": " + host);
    }
    String label = host.substring(0, host.length() - suffix.length());
    int dash = label.lastIndexOf('-');
    if (dash <= 0 || dash == label.length() - 1) {
      throw new IllegalArgumentException("host has no user-port form: " + host);
    }
    String slug = label.substring(0, dash);
    int port;
    try {
      port = Integer.parseInt(label.substring(dash + 1));
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException("host port is not numeric: " + host);
    }
    return new ParsedHost(slug, port);
  }
}
