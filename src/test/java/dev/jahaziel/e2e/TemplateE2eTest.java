/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Workspace templates over HTTP, served from the seeded temp templates directory. */
class TemplateE2eTest extends E2eBase {
  @Test
  void listContainsSeededTemplates() throws Exception {
    E2eResponse listed = get("/api/v1/templates", userToken());
    assertEquals(200, listed.status(), listed.body());
    assertTrue(listed.body().contains("\"empty\""), listed.body());
    assertTrue(listed.body().contains("\"node\""), listed.body());
  }

  @Test
  void getReturnsTemplateJson() throws Exception {
    E2eResponse fetched = get("/api/v1/templates/empty", userToken());
    assertEquals(200, fetched.status(), fetched.body());
    assertTrue(fetched.body().contains("\"vcpuCount\":2"), fetched.body());
  }

  @Test
  void getUnknownIsNotFound() throws Exception {
    assertEquals(404, get("/api/v1/templates/missing-template", userToken()).status());
  }

  @Test
  void applyCreatesWorkspace() throws Exception {
    String name = uniqueName("from-template");

    E2eResponse applied =
        post("/api/v1/templates/empty/apply", userToken(), "{\"workspaceName\":\"" + name + "\"}");
    assertEquals(201, applied.status(), applied.body());
    assertTrue(applied.body().contains("\"name\":\"" + name + "\""), applied.body());
    String id = bodyJson(applied).get("workspaceId").asString();

    assertEquals(200, get("/api/v1/workspaces/" + id, userToken()).status());
  }

  @Test
  void applyUnknownTemplateFails() throws Exception {
    // No template file: the read blows up into the generic 500 handler.
    E2eResponse applied =
        post(
            "/api/v1/templates/missing-template/apply",
            userToken(),
            "{\"workspaceName\":\"" + uniqueName("nope") + "\"}");
    assertEquals(500, applied.status(), applied.body());
    assertTrue(applied.body().contains("INTERNAL_ERROR"), applied.body());
  }

  @Test
  void applyBlankNameIsRejected() throws Exception {
    E2eResponse applied =
        post("/api/v1/templates/empty/apply", userToken(), "{\"workspaceName\":\"\"}");
    assertEquals(400, applied.status(), applied.body());
  }

  @Test
  void traversalNameIsRejected() throws Exception {
    E2eResponse applied =
        post(
            "/api/v1/templates/a..b/apply",
            userToken(),
            "{\"workspaceName\":\"" + uniqueName("trap") + "\"}");
    assertEquals(400, applied.status(), applied.body());
  }

  @Test
  void anonymousListIsUnauthorized() throws Exception {
    E2eResponse listed = getAnonymous("/api/v1/templates");
    assertEquals(401, listed.status(), listed.body());
    assertTrue(listed.body().contains("AUTH_REQUIRED"), listed.body());
  }
}
