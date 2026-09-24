/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.domain.UserId;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Bearer authentication: 401 shapes, invalid tokens, cookie behavior with IdP disabled. */
class AuthnE2eTest extends E2eBase {
  @Test
  void anonymousApiIsUnauthorizedJson() throws Exception {
    E2eResponse denied = getAnonymous("/api/v1/me");
    assertEquals(401, denied.status(), denied.body());
    assertTrue(denied.body().contains("\"code\":\"AUTH_REQUIRED\""), denied.body());
    assertTrue(denied.body().contains("\"message\":\"Authentication required\""), denied.body());
    assertTrue(denied.body().contains("correlationId"), denied.body());
  }

  @Test
  void htmlAcceptWithoutIdpStaysUnauthorized() throws Exception {
    // Login is disabled (no BASIS_*), so no /api/login redirect happens; assert actual.
    E2eResponse denied = getHtml("/api/v1/me");
    assertEquals(401, denied.status(), denied.body());
  }

  @Test
  void malformedBearerIsRejected() throws Exception {
    E2eResponse denied = get("/api/v1/me", "nope");
    assertEquals(401, denied.status(), denied.body());
    assertTrue(denied.body().contains("\"code\":\"AUTH_INVALID\""), denied.body());
    assertTrue(
        denied.headers().firstValue("www-authenticate").isPresent(), denied.headers().toString());
  }

  @Test
  void tamperedBearerIsRejected() throws Exception {
    E2eResponse denied = get("/api/v1/me", userToken() + "x");
    assertEquals(401, denied.status(), denied.body());
    assertTrue(denied.body().contains("AUTH_INVALID"), denied.body());
  }

  @Test
  void tokenForUnknownUserIsRejected() throws Exception {
    String orphan =
        tokens.issue(new UserId(UUID.randomUUID()), null, List.of("e2e"), Duration.ofMinutes(5));

    E2eResponse denied = get("/api/v1/me", orphan);
    assertEquals(401, denied.status(), denied.body());
  }

  @Test
  void bffMeRoundTrip() throws Exception {
    E2eResponse me = get("/api/auth/me", userToken());
    assertEquals(200, me.status(), me.body());
    assertTrue(me.body().contains("e2e-user@example.test"), me.body());
    assertEquals(401, getAnonymous("/api/auth/me").status());
  }

  @Test
  void logoutClearsCookies() throws Exception {
    E2eResponse loggedOut = post("/api/auth/logout", userToken(), null);
    assertEquals(204, loggedOut.status(), loggedOut.body());
    String cookies = loggedOut.allHeaders("set-cookie");
    assertTrue(cookies.contains("basis_sites_session="), cookies);
    assertTrue(cookies.contains("token="), cookies);
    assertTrue(cookies.contains("Max-Age=0"), cookies);
  }

  @Test
  void cookieWithoutBearerIsIgnoredWhenIdpDisabled() throws Exception {
    // BffAuthenticationFilter only runs when DevConnect is configured; here cookies do nothing.
    E2eResponse denied = getWithCookie("/api/v1/me", "token=" + userToken());
    assertEquals(401, denied.status(), denied.body());
  }
}
