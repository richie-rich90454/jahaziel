/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Snapshots over HTTP against folder storage. Snapshot deletion has no route (see {@link
 * #noDeleteRouteDocumentsAbsence}), so only create/list/restore run here.
 */
class SnapshotE2eTest extends E2eBase {
  @Test
  void createAndListRoundTrip() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("snapped"));
    String tag = uniqueTag();

    E2eResponse created =
        post("/api/v1/workspaces/" + id + "/snapshots", userToken(), "{\"tag\":\"" + tag + "\"}");
    assertEquals(201, created.status(), created.body());
    assertTrue(created.body().contains("\"tag\":\"" + tag + "\""), created.body());
    String snapshotId = bodyJson(created).get("snapshotId").asString();

    E2eResponse listed = get("/api/v1/workspaces/" + id + "/snapshots", userToken());
    assertEquals(200, listed.status(), listed.body());
    assertTrue(listed.body().contains(tag), listed.body());
    assertTrue(listed.body().contains(snapshotId), listed.body());
  }

  @Test
  void duplicateTagIsRejected() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("dtag"));
    String tag = uniqueTag();
    post("/api/v1/workspaces/" + id + "/snapshots", userToken(), "{\"tag\":\"" + tag + "\"}");

    E2eResponse again =
        post("/api/v1/workspaces/" + id + "/snapshots", userToken(), "{\"tag\":\"" + tag + "\"}");
    assertEquals(400, again.status(), again.body());
    assertTrue(again.body().contains("VALIDATION_FAILED"), again.body());
  }

  @Test
  void badTagIsRejected() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("btag"));

    assertEquals(
        400,
        post("/api/v1/workspaces/" + id + "/snapshots", userToken(), "{\"tag\":\"\"}").status());
    E2eResponse spaced =
        post("/api/v1/workspaces/" + id + "/snapshots", userToken(), "{\"tag\":\"bad tag!\"}");
    assertEquals(400, spaced.status(), spaced.body());
  }

  @Test
  void restoreReturnsWorkspace() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("restore"));
    String tag = uniqueTag();
    E2eResponse created =
        post("/api/v1/workspaces/" + id + "/snapshots", userToken(), "{\"tag\":\"" + tag + "\"}");
    String snapshotId = bodyJson(created).get("snapshotId").asString();

    E2eResponse restored =
        post(
            "/api/v1/workspaces/" + id + "/snapshots/" + snapshotId + "/restore",
            userToken(),
            null);
    assertEquals(200, restored.status(), restored.body());
    assertTrue(restored.body().contains("\"workspaceId\":\"" + id + "\""), restored.body());
  }

  @Test
  void restoreUnknownSnapshotIsNotFound() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("nosnap"));

    E2eResponse restored =
        post(
            "/api/v1/workspaces/" + id + "/snapshots/" + UUID.randomUUID() + "/restore",
            userToken(),
            null);
    assertEquals(404, restored.status(), restored.body());
    assertTrue(restored.body().contains("\"code\":\"NOT_FOUND\""), restored.body());
  }

  @Test
  void restoreForeignSnapshotIsRejected() throws Exception {
    String first = createWorkspace(userToken(), uniqueName("own-a"));
    String second = createWorkspace(userToken(), uniqueName("own-b"));
    String tag = uniqueTag();
    E2eResponse created =
        post(
            "/api/v1/workspaces/" + first + "/snapshots", userToken(), "{\"tag\":\"" + tag + "\"}");
    String snapshotId = bodyJson(created).get("snapshotId").asString();

    E2eResponse restored =
        post(
            "/api/v1/workspaces/" + second + "/snapshots/" + snapshotId + "/restore",
            userToken(),
            null);
    assertEquals(400, restored.status(), restored.body());
  }

  @Test
  void snapshotOnUnknownWorkspaceIsNotFound() throws Exception {
    String missing = UUID.randomUUID().toString();
    assertEquals(
        404,
        post(
                "/api/v1/workspaces/" + missing + "/snapshots",
                userToken(),
                "{\"tag\":\"" + uniqueTag() + "\"}")
            .status());
    assertEquals(404, get("/api/v1/workspaces/" + missing + "/snapshots", userToken()).status());
  }

  @Test
  void noDeleteRouteDocumentsAbsence() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("nodel"));

    E2eResponse deleted =
        delete("/api/v1/workspaces/" + id + "/snapshots/" + UUID.randomUUID(), userToken());
    assertEquals(404, deleted.status(), deleted.body());
    assertTrue(deleted.body().contains("NOT_FOUND"), deleted.body());
  }
}
