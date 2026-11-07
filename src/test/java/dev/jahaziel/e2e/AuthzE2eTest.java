/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Authorization through the API: ownership checks, admin-only routes, and the suspended-bearer
 * behavior (asserted as observed).
 */
class AuthzE2eTest extends E2eBase {
  @Test
  void nonOwnerCannotRead() throws Exception {
    String id = createWorkspace(adminToken(), uniqueName("theirs"));

    E2eResponse fetched = get("/api/v1/workspaces/" + id, userToken());
    assertEquals(403, fetched.status(), fetched.body());
    assertTrue(fetched.body().contains("\"code\":\"FORBIDDEN\""), fetched.body());
    assertTrue(fetched.body().contains("\"action\":\"read\""), fetched.body());

    assertEquals(403, get("/api/v1/workspaces/" + id + "/snapshots", userToken()).status());
    assertEquals(403, get("/api/v1/workspaces/" + id + "/terminals", userToken()).status());
    assertEquals(403, get("/api/v1/workspaces/" + id + "/ports", userToken()).status());
    assertEquals(403, get("/api/v1/workspaces/" + id + "/metrics", userToken()).status());
    assertEquals(403, get("/api/v1/workspaces/" + id + "/git", userToken()).status());
    assertEquals(
        403, get("/api/v1/workspaces/" + id + "/git/diff?path=README.md", userToken()).status());
    assertEquals(
        403, get("/api/v1/workspaces/" + id + "/files/search?q=secret", userToken()).status());
  }

  @Test
  void nonOwnerCannotWrite() throws Exception {
    String id = createWorkspace(adminToken(), uniqueName("locked"));

    assertEquals(403, post("/api/v1/workspaces/" + id + "/start", userToken(), null).status());
    assertEquals(403, post("/api/v1/workspaces/" + id + "/stop", userToken(), null).status());
    assertEquals(
        403,
        post(
                "/api/v1/workspaces/" + id + "/snapshots",
                userToken(),
                "{\"tag\":\"" + uniqueTag() + "\"}")
            .status());
    assertEquals(403, post("/api/v1/workspaces/" + id + "/terminals", userToken(), null).status());
    assertEquals(
        403,
        post(
                "/api/v1/workspaces/" + id + "/ports",
                userToken(),
                "{\"port\":18090,\"protocol\":\"http\"}")
            .status());
    assertEquals(
        403,
        post("/api/v1/workspaces/" + id + "/git/stage", userToken(), "{\"path\":\"README.md\"}")
            .status());
    assertEquals(
        403,
        post(
                "/api/v1/workspaces/" + id + "/git/commit",
                userToken(),
                "{\"message\":\"do not commit\"}")
            .status());
    assertEquals(403, delete("/api/v1/workspaces/" + id + "?confirm=true", userToken()).status());
  }

  @Test
  void nonOwnerCannotCloseForeignTerminal() throws Exception {
    Assumptions.assumeTrue(
        System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux"),
        "PTY authorization integration test requires the Linux server runtime");
    String id = createWorkspace(adminToken(), uniqueName("fterm"));
    assertEquals(200, post("/api/v1/workspaces/" + id + "/start", adminToken(), null).status());
    String sessionId =
        bodyJson(post("/api/v1/workspaces/" + id + "/terminals", adminToken(), null))
            .get("sessionId")
            .asString();

    E2eResponse closed = delete("/api/v1/terminals/" + sessionId, userToken());
    assertEquals(403, closed.status(), closed.body());
  }

  @Test
  void nonOwnerCannotTouchForeignPort() throws Exception {
    String id = createWorkspace(adminToken(), uniqueName("fport"));
    post(
        "/api/v1/workspaces/" + id + "/ports",
        adminToken(),
        "{\"port\":18091,\"protocol\":\"http\"}");

    E2eResponse removed = delete("/api/v1/workspaces/" + id + "/ports/18091", userToken());
    assertEquals(403, removed.status(), removed.body());
  }

  @Test
  void plainUserCannotUseAdminApis() throws Exception {
    assertEquals(403, get("/admin/v1/users", userToken()).status());
    assertEquals(403, get("/admin/v1/health", userToken()).status());
    assertEquals(403, get("/admin/v1/audit/query", userToken()).status());
    assertEquals(403, get("/admin/v1/workspaces", userToken()).status());
  }

  @Test
  void adminBypassesOwnership() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("pleb"));

    E2eResponse fetched = get("/api/v1/workspaces/" + id, adminToken());
    assertEquals(200, fetched.status(), fetched.body());

    E2eResponse deleted = post("/admin/v1/workspaces/" + id + "/delete", adminToken(), null);
    assertEquals(204, deleted.status(), deleted.body());
  }

  @Test
  void suspendedBearerStillPassesApi() throws Exception {
    // Observed: only cookie sessions re-check the account on every request; bearer
    // tokens stay valid until expiry. Documents current behavior, not the ideal.
    E2eResponse me = get("/api/v1/me", suspendedToken());
    assertEquals(200, me.status(), me.body());
    assertTrue(me.body().contains("e2e-suspended@example.test"), me.body());
  }
}
