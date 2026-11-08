/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.security.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class BrowserOriginTest {
  private static final URI BACKEND_URI = URI.create("http://127.0.0.1:8080/api/v1/stream");

  @Test
  void acceptsPublicHttpsOriginDescribedByTrustedProxyHeaders() {
    assertTrue(
        BrowserOrigin.matchesIfPresent(
            "https://ide.example",
            "https, http",
            "ide.example, proxy.internal",
            "127.0.0.1:8080",
            BACKEND_URI));
  }

  @Test
  void rejectsDifferentHostsAndSchemes() {
    assertFalse(
        BrowserOrigin.matchesIfPresent(
            "https://attacker.example", "https", "ide.example", "127.0.0.1:8080", BACKEND_URI));
    assertFalse(
        BrowserOrigin.matchesIfPresent(
            "http://ide.example", "https", "ide.example", "127.0.0.1:8080", BACKEND_URI));
  }

  @Test
  void rejectsMalformedOriginsAndProxyAuthorities() {
    assertFalse(
        BrowserOrigin.matchesIfPresent(
            "https://ide.example/path", "https", "ide.example", "127.0.0.1:8080", BACKEND_URI));
    assertFalse(
        BrowserOrigin.matchesIfPresent(
            "https://ide.example", "https", "ide.example/path", "127.0.0.1:8080", BACKEND_URI));
  }

  @Test
  void permitsRequestsWithoutAnOriginHeader() {
    assertTrue(BrowserOrigin.matchesIfPresent(null, null, null, null, BACKEND_URI));
  }
}
