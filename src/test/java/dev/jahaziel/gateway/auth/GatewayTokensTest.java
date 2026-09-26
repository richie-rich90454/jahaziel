/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.gateway.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Token carriers of {@link GatewayTokens}. */
class GatewayTokensTest {
  @Test
  void bearerReadsAuthorizationHeader() {
    assertEquals("tok", GatewayTokens.bearer(Map.of("authorization", "Bearer tok")));
    assertEquals("tok", GatewayTokens.bearer(Map.of("authorization", "Bearer   tok  ")));
    assertNull(GatewayTokens.bearer(Map.of("authorization", "Basic abc")));
    assertNull(GatewayTokens.bearer(Map.of()));
    assertThrows(NullPointerException.class, () -> GatewayTokens.bearer(null));
  }

  @Test
  void queryTokenReadsTokenParameter() {
    assertEquals("tok", GatewayTokens.queryToken("/path?token=tok"));
    assertEquals("tok", GatewayTokens.queryToken("/path?a=1&token=tok&b=2"));
    assertNull(GatewayTokens.queryToken("/path"));
    assertNull(GatewayTokens.queryToken("/path?other=1"));
    assertNull(GatewayTokens.queryToken("/path?token"));
    assertThrows(NullPointerException.class, () -> GatewayTokens.queryToken(null));
  }

  @Test
  void cookieTokenReadsSessionCookie() {
    assertEquals("tok", GatewayTokens.cookieToken("a=b; jahaziel-session=tok"));
    assertEquals("tok", GatewayTokens.cookieToken("jahaziel-session=tok; a=b"));
    assertEquals("tok", GatewayTokens.cookieToken("jahaziel-session = tok "));
    assertNull(GatewayTokens.cookieToken("a=b"));
    assertNull(GatewayTokens.cookieToken("lonely"));
    assertNull(GatewayTokens.cookieToken(null));
  }

  @Test
  void firstTokenPrefersBearerThenQueryThenCookie() {
    assertEquals(
        "bearer",
        GatewayTokens.firstToken(Map.of("authorization", "Bearer bearer"), "/?token=query"));
    assertEquals("query", GatewayTokens.firstToken(Map.of(), "/?token=query"));
    assertEquals(
        "cookie", GatewayTokens.firstToken(Map.of("cookie", "jahaziel-session=cookie"), "/path"));
    assertNull(GatewayTokens.firstToken(Map.of(), "/path"));
  }
}
