/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Validation and URL building of {@link GatewayEndpoint}. */
class GatewayEndpointTest {
  @Test
  void localUrlCarriesNonDefaultPort() {
    var endpoint = new GatewayEndpoint("http", "localhost", 8081);
    assertEquals(
        "http://richard-9000.localhost:8081/?token=tok", endpoint.url("richard", 9000, "tok"));
  }

  @Test
  void defaultPortsAreOmitted() {
    assertEquals(
        "https://richard-443.code.example.com/?token=tok",
        new GatewayEndpoint("https", "code.example.com", 443).url("richard", 443, "tok"));
    assertEquals(
        "http://richard-80.code.example.com/?token=tok",
        new GatewayEndpoint("http", "code.example.com", 80).url("richard", 80, "tok"));
    assertEquals(
        "https://richard-8443.code.example.com:8443/?token=tok",
        new GatewayEndpoint("https", "code.example.com", 8443).url("richard", 8443, "tok"));
  }

  @Test
  void rejectsBadInputs() {
    assertThrows(IllegalArgumentException.class, () -> new GatewayEndpoint("ftp", "x.test", 8081));
    assertThrows(IllegalArgumentException.class, () -> new GatewayEndpoint(null, "x.test", 8081));
    assertThrows(NullPointerException.class, () -> new GatewayEndpoint("http", null, 8081));
    assertThrows(IllegalArgumentException.class, () -> new GatewayEndpoint("http", "  ", 8081));
    assertThrows(IllegalArgumentException.class, () -> new GatewayEndpoint("http", "x.test", -1));
    assertThrows(
        IllegalArgumentException.class, () -> new GatewayEndpoint("http", "x.test", 65536));
    assertEquals(0, new GatewayEndpoint("http", "x.test", 0).port());
    var endpoint = new GatewayEndpoint("http", "x.test", 8081);
    assertThrows(NullPointerException.class, () -> endpoint.url(null, 8081, "tok"));
    assertThrows(NullPointerException.class, () -> endpoint.url("slug", 8081, null));
  }
}
