/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.persistence.AppUserEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Administration APIs: every call needs the admin role plus enrolled MFA. */
class AdminE2eTest extends E2eBase {
  @Test
  void usersListsSeededAccounts() throws Exception {
    E2eResponse listed = get("/admin/v1/users", adminToken());
    assertEquals(200, listed.status(), listed.body());
    assertTrue(listed.body().contains("e2e-admin@example.test"), listed.body());
    assertTrue(listed.body().contains("e2e-user@example.test"), listed.body());
    assertTrue(listed.body().contains("e2e-suspended@example.test"), listed.body());
  }

  @Test
  void plainUserCannotListUsers() throws Exception {
    E2eResponse listed = get("/admin/v1/users", userToken());
    assertEquals(403, listed.status(), listed.body());
    assertTrue(listed.body().contains("\"code\":\"FORBIDDEN\""), listed.body());
  }

  @Test
  void adminWithoutMfaIsRejected() throws Exception {
    E2eResponse listed = get("/admin/v1/users", nomfaAdminToken());
    assertEquals(403, listed.status(), listed.body());
    assertTrue(listed.body().contains("FORBIDDEN"), listed.body());
  }

  @Test
  void suspendUnsuspendCycle() throws Exception {
    UUID target = freshUser();

    E2eResponse suspended =
        post("/admin/v1/users/" + target + "/suspend", adminToken(), "{\"reason\":\"e2e\"}");
    assertEquals(204, suspended.status(), suspended.body());
    assertEquals("suspended", userRows.findById(target).orElseThrow().getStatus());

    E2eResponse unsuspended =
        post("/admin/v1/users/" + target + "/unsuspend", adminToken(), "{\"reason\":\"e2e\"}");
    assertEquals(204, unsuspended.status(), unsuspended.body());
    assertEquals("active", userRows.findById(target).orElseThrow().getStatus());
  }

  @Test
  void suspendBlankReasonIsRejected() throws Exception {
    E2eResponse suspended =
        post("/admin/v1/users/" + USER_ID + "/suspend", adminToken(), "{\"reason\":\"\"}");
    assertEquals(400, suspended.status(), suspended.body());
  }

  @Test
  void suspendUnknownUserIsNotFound() throws Exception {
    E2eResponse suspended =
        post(
            "/admin/v1/users/" + UUID.randomUUID() + "/suspend",
            adminToken(),
            "{\"reason\":\"e2e\"}");
    assertEquals(404, suspended.status(), suspended.body());
  }

  @Test
  void workspacesListsAll() throws Exception {
    String name = uniqueName("visible");
    createWorkspace(userToken(), name);

    E2eResponse listed = get("/admin/v1/workspaces", adminToken());
    assertEquals(200, listed.status(), listed.body());
    assertTrue(listed.body().contains(name), listed.body());
  }

  @Test
  void adminDeletesWorkspace() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("doomed"));

    E2eResponse deleted = post("/admin/v1/workspaces/" + id + "/delete", adminToken(), null);
    assertEquals(204, deleted.status(), deleted.body());
    assertEquals(404, get("/api/v1/workspaces/" + id, userToken()).status());

    E2eResponse missing =
        post("/admin/v1/workspaces/" + UUID.randomUUID() + "/delete", adminToken(), null);
    assertEquals(404, missing.status(), missing.body());
  }

  @Test
  void nodesListIsEmptyAndDrainUnknownIsNotFound() throws Exception {
    E2eResponse listed = get("/admin/v1/nodes", adminToken());
    assertEquals(200, listed.status(), listed.body());
    assertEquals("[]", listed.body().trim());

    E2eResponse drained = post("/admin/v1/nodes/node-missing/drain", adminToken(), null);
    assertEquals(404, drained.status(), drained.body());
  }

  @Test
  void auditQueryServesEvents() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("audited"));
    String tag = uniqueTag();
    post("/api/v1/workspaces/" + id + "/snapshots", userToken(), "{\"tag\":\"" + tag + "\"}");

    // AuditRecordDto carries action/target but not the detail map; assert the recorded event.
    E2eResponse queried = get("/admin/v1/audit/query?limit=50", adminToken());
    assertEquals(200, queried.status(), queried.body());
    assertTrue(queried.body().contains("\"action\":\"snapshot.create\""), queried.body());
    assertTrue(queried.body().contains("\"target\":\"" + id + "\""), queried.body());

    E2eResponse badLimit = get("/admin/v1/audit/query?limit=0", adminToken());
    assertEquals(400, badLimit.status(), badLimit.body());
  }

  @Test
  void policyCrudRoundTrip() throws Exception {
    String marker = UUID.randomUUID().toString().substring(0, 8);
    String doc = "{\"allow\":true,\"e2e\":\"" + marker + "\"}";

    E2eResponse created =
        post(
            "/admin/v1/policy",
            adminToken(),
            "{\"scope\":\"global\",\"scopeId\":null,\"policyJson\":" + quoted(doc) + "}");
    assertEquals(201, created.status(), created.body());
    String policyId = bodyJson(created).get("id").asString();

    E2eResponse listed = get("/admin/v1/policy?scope=global", adminToken());
    assertEquals(200, listed.status(), listed.body());
    assertTrue(listed.body().contains(marker), listed.body());

    assertEquals(204, delete("/admin/v1/policy/" + policyId, adminToken()).status());
    E2eResponse again = delete("/admin/v1/policy/" + policyId, adminToken());
    assertEquals(404, again.status(), again.body());

    E2eResponse badScope =
        post(
            "/admin/v1/policy",
            adminToken(),
            "{\"scope\":\"bogus\",\"scopeId\":null,\"policyJson\":" + quoted(doc) + "}");
    assertEquals(400, badScope.status(), badScope.body());
  }

  @Test
  void metricsAndHealth() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("observed"));

    E2eResponse metrics = get("/admin/v1/metrics?workspaceId=" + id, adminToken());
    assertEquals(200, metrics.status(), metrics.body());
    assertEquals("[]", metrics.body().trim());

    E2eResponse health = get("/admin/v1/health", adminToken());
    assertEquals(200, health.status(), health.body());
    assertTrue(health.body().contains("\"status\":\"ok\""), health.body());
    assertTrue(health.body().contains("\"nodes\":0"), health.body());
  }

  private UUID freshUser() {
    UUID id = UUID.randomUUID();
    AppUserEntity row = new AppUserEntity();
    row.setUserId(id);
    row.setExternalId("e2e-fresh-" + id);
    row.setEmail("e2e-fresh-" + id + "@example.test");
    row.setDisplayName("E2E Fresh");
    row.setCreatedAt(Instant.now());
    row.setStatus("active");
    row.setMfaEnabled(false);
    userRows.save(row);
    return id;
  }

  private static String quoted(String raw) {
    return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
