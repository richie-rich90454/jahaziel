/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Workspace CRUD over HTTP against the real portable stack. */
class WorkspaceE2eTest extends E2eBase {
  @Test
  void meReturnsSeededIdentity() throws Exception {
    E2eResponse me = get("/api/v1/me", userToken());
    assertEquals(200, me.status(), me.body());
    assertTrue(me.body().contains("e2e-user@example.test"), me.body());
    assertTrue(me.body().contains("ROLE_USER"), me.body());
  }

  @Test
  void meReportsMfaForAdmin() throws Exception {
    E2eResponse me = get("/api/v1/me", adminToken());
    assertEquals(200, me.status(), me.body());
    assertTrue(me.body().contains("\"mfaEnabled\":true"), me.body());
    assertTrue(me.body().contains("ROLE_ADMIN"), me.body());
  }

  @Test
  void meWorkspacesListsOnlyOwned() throws Exception {
    String name = uniqueName("mine");
    createWorkspace(userToken(), name);

    E2eResponse mine = get("/api/v1/me/workspaces", userToken());
    assertEquals(200, mine.status(), mine.body());
    assertTrue(mine.body().contains(name), mine.body());

    E2eResponse adminView = get("/api/v1/me/workspaces", adminToken());
    assertEquals(200, adminView.status(), adminView.body());
    assertTrue(!adminView.body().contains(name), adminView.body());
  }

  @Test
  void createThenGetRoundTripWithQuotaView() throws Exception {
    String name = uniqueName("club");
    String id = createWorkspace(userToken(), name);

    E2eResponse fetched = get("/api/v1/workspaces/" + id, userToken());
    assertEquals(200, fetched.status(), fetched.body());
    assertTrue(fetched.body().contains("\"name\":\"" + name + "\""), fetched.body());
    assertTrue(fetched.body().contains("\"state\":\"requested\""), fetched.body());
    assertTrue(fetched.body().contains("\"quota\""), fetched.body());
    assertTrue(fetched.body().contains("\"logicalBytesLimit\""), fetched.body());
    assertTrue(fetched.body().contains("\"ports\":[]"), fetched.body());
  }

  @Test
  void duplicateNameIsRejected() throws Exception {
    String name = uniqueName("dup");
    createWorkspace(userToken(), name);

    E2eResponse again = post("/api/v1/workspaces", userToken(), "{\"name\":\"" + name + "\"}");
    assertEquals(400, again.status(), again.body());
    assertTrue(again.body().contains("VALIDATION_FAILED"), again.body());
  }

  @Test
  void badInputIsRejected() throws Exception {
    assertEquals(400, post("/api/v1/workspaces", userToken(), "{\"name\":\"\"}").status());
    assertEquals(
        400,
        post(
                "/api/v1/workspaces",
                userToken(),
                "{\"name\":\"" + uniqueName("mem") + "\",\"memoryMib\":1}")
            .status());
    assertEquals(
        400,
        post(
                "/api/v1/workspaces",
                userToken(),
                "{\"name\":\"" + uniqueName("cpu") + "\",\"vcpuCount\":9}")
            .status());
    String longName = "n".repeat(65);
    E2eResponse tooLong =
        post("/api/v1/workspaces", userToken(), "{\"name\":\"" + longName + "\"}");
    assertEquals(400, tooLong.status(), tooLong.body());
  }

  @Test
  void unknownWorkspaceIsNotFoundWithCorrelation() throws Exception {
    E2eResponse fetched = get("/api/v1/workspaces/" + UUID.randomUUID(), userToken());
    assertEquals(404, fetched.status(), fetched.body());
    assertTrue(fetched.body().contains("\"code\":\"NOT_FOUND\""), fetched.body());
    assertTrue(fetched.body().contains("correlationId"), fetched.body());
  }

  @Test
  void deleteNeedsConfirmThenRemoves() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("gone"));

    E2eResponse unconfirmed = delete("/api/v1/workspaces/" + id, userToken());
    assertEquals(400, unconfirmed.status(), unconfirmed.body());

    E2eResponse deleted = delete("/api/v1/workspaces/" + id + "?confirm=true", userToken());
    assertEquals(204, deleted.status(), deleted.body());

    assertEquals(404, get("/api/v1/workspaces/" + id, userToken()).status());
    E2eResponse nochmal = delete("/api/v1/workspaces/" + id + "?confirm=true", userToken());
    assertEquals(404, nochmal.status(), nochmal.body());
  }
}
