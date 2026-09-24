/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Terminal sessions over HTTP; the registry is in memory, PTY relay lives in the VM. */
class TerminalE2eTest extends E2eBase {
  @Test
  void openListCloseRoundTrip() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("term"));

    E2eResponse opened =
        post("/api/v1/workspaces/" + id + "/terminals?rows=40&cols=100", userToken(), null);
    assertEquals(201, opened.status(), opened.body());
    assertTrue(opened.body().contains("\"rows\":40"), opened.body());
    assertTrue(opened.body().contains("\"cols\":100"), opened.body());
    String sessionId = bodyJson(opened).get("sessionId").asString();

    E2eResponse listed = get("/api/v1/workspaces/" + id + "/terminals", userToken());
    assertEquals(200, listed.status(), listed.body());
    assertTrue(listed.body().contains(sessionId), listed.body());

    E2eResponse closed = delete("/api/v1/terminals/" + sessionId, userToken());
    assertEquals(204, closed.status(), closed.body());

    E2eResponse relisted = get("/api/v1/workspaces/" + id + "/terminals", userToken());
    assertTrue(!relisted.body().contains(sessionId), relisted.body());
  }

  @Test
  void defaultSizeApplies() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("sized"));

    E2eResponse opened = post("/api/v1/workspaces/" + id + "/terminals", userToken(), null);
    assertEquals(201, opened.status(), opened.body());
    assertTrue(opened.body().contains("\"rows\":30"), opened.body());
    assertTrue(opened.body().contains("\"cols\":120"), opened.body());
  }

  @Test
  void closeTwiceIsNotFound() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("twice"));
    E2eResponse opened = post("/api/v1/workspaces/" + id + "/terminals", userToken(), null);
    String sessionId = bodyJson(opened).get("sessionId").asString();

    assertEquals(204, delete("/api/v1/terminals/" + sessionId, userToken()).status());
    E2eResponse again = delete("/api/v1/terminals/" + sessionId, userToken());
    assertEquals(404, again.status(), again.body());
    assertTrue(again.body().contains("\"code\":\"NOT_FOUND\""), again.body());
  }

  @Test
  void closeUnknownIsNotFound() throws Exception {
    E2eResponse closed = delete("/api/v1/terminals/" + UUID.randomUUID(), userToken());
    assertEquals(404, closed.status(), closed.body());
  }

  @Test
  void openOnUnknownWorkspaceIsNotFound() throws Exception {
    assertEquals(
        404,
        post("/api/v1/workspaces/" + UUID.randomUUID() + "/terminals", userToken(), null).status());
  }

  @Test
  void listOnUnknownWorkspaceIsNotFound() throws Exception {
    assertEquals(
        404, get("/api/v1/workspaces/" + UUID.randomUUID() + "/terminals", userToken()).status());
  }
}
