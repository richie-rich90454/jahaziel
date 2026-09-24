/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** DevConnect login endpoints with no IdP configured: every path fails closed. */
class DevConnectE2eTest extends E2eBase {
  @Test
  void loginWithoutIdpFailsClosed() throws Exception {
    E2eResponse login = getAnonymous("/api/login");
    assertEquals(500, login.status(), login.body());
    assertTrue(login.body().contains("DEVCONNECT_FAILED"), login.body());
  }

  @Test
  void callbackErrorRedirectsToFailure() throws Exception {
    E2eResponse callback = getNoRedirect("/api/oauth/callback?error=access_denied");
    assertEquals(302, callback.status(), callback.body());
    assertTrue(
        callback.header("location").contains("?login=failed"), callback.headers().toString());
  }

  @Test
  void callbackWithoutParamsFails() throws Exception {
    // IdP is not configured, so requireConfigured fires before param checks; assert actual.
    E2eResponse callback = getAnonymous("/api/oauth/callback");
    assertEquals(500, callback.status(), callback.body());
    assertTrue(callback.body().contains("DEVCONNECT_FAILED"), callback.body());
  }

  @Test
  void callbackWithBogusStateFails() throws Exception {
    E2eResponse callback = getAnonymous("/api/oauth/callback?state=bogus&code=bogus");
    assertEquals(500, callback.status(), callback.body());
  }

  @Test
  void logoutNeedsAuthentication() throws Exception {
    // /api/auth/logout is not in the permitAll set, so anonymous logout is rejected.
    E2eResponse anonymous = post("/api/auth/logout", null, null);
    assertEquals(401, anonymous.status(), anonymous.body());
    assertTrue(anonymous.body().contains("AUTH_REQUIRED"), anonymous.body());

    E2eResponse loggedOut = post("/api/auth/logout", userToken(), null);
    assertEquals(204, loggedOut.status(), loggedOut.body());
    assertTrue(
        loggedOut.allHeaders("set-cookie").contains("Max-Age=0"),
        loggedOut.allHeaders("set-cookie"));
  }

  @Test
  void authMeAnonymousIsUnauthorized() throws Exception {
    E2eResponse me = getAnonymous("/api/auth/me");
    assertEquals(401, me.status(), me.body());
    assertTrue(me.body().contains("AUTH_REQUIRED"), me.body());
  }
}
