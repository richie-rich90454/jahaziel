/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Runtime lifecycle over HTTP: the local backend really prepares, boots, and parks. */
class RuntimeE2eTest extends E2eBase {
  @Test
  void startMovesRequestedToBooting() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("run"));

    E2eResponse started = post("/api/v1/workspaces/" + id + "/start", userToken(), null);
    assertEquals(200, started.status(), started.body());
    assertTrue(started.body().contains("\"state\":\"booting\""), started.body());
  }

  @Test
  void startTwiceConvergesWithoutConflict() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("twice"));

    assertEquals(200, post("/api/v1/workspaces/" + id + "/start", userToken(), null).status());
    E2eResponse again = post("/api/v1/workspaces/" + id + "/start", userToken(), null);
    assertEquals(200, again.status(), again.body());
    assertTrue(again.body().contains("\"state\":\"booting\""), again.body());
  }

  @Test
  void stopParksRunningWorkspace() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("park"));
    post("/api/v1/workspaces/" + id + "/start", userToken(), null);

    E2eResponse stopped = post("/api/v1/workspaces/" + id + "/stop", userToken(), null);
    assertEquals(200, stopped.status(), stopped.body());
    assertTrue(stopped.body().contains("\"state\":\"suspended\""), stopped.body());
  }

  @Test
  void stopFreshWorkspaceIsNoop() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("fresh"));

    E2eResponse stopped = post("/api/v1/workspaces/" + id + "/stop", userToken(), null);
    assertEquals(200, stopped.status(), stopped.body());
    assertTrue(stopped.body().contains("\"state\":\"requested\""), stopped.body());
  }

  @Test
  void restartSuspendedResumesCycle() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("cycle"));
    post("/api/v1/workspaces/" + id + "/start", userToken(), null);
    post("/api/v1/workspaces/" + id + "/stop", userToken(), null);

    E2eResponse restarted = post("/api/v1/workspaces/" + id + "/restart", userToken(), null);
    assertEquals(200, restarted.status(), restarted.body());
    assertTrue(restarted.body().contains("\"state\":\"booting\""), restarted.body());
  }

  @Test
  void restartFreshWorkspaceConflicts() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("norestart"));

    E2eResponse restarted = post("/api/v1/workspaces/" + id + "/restart", userToken(), null);
    assertEquals(409, restarted.status(), restarted.body());
    assertTrue(restarted.body().contains("STATE_CONFLICT"), restarted.body());
  }

  @Test
  void metricsAreZeroBeforeSampler() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("metered"));

    E2eResponse metrics = get("/api/v1/workspaces/" + id + "/metrics", userToken());
    assertEquals(200, metrics.status(), metrics.body());
    assertTrue(metrics.body().contains("\"cpuUsagePercent\":0"), metrics.body());
  }

  @Test
  void unknownWorkspaceLifecycleIsNotFound() throws Exception {
    String missing = UUID.randomUUID().toString();
    assertEquals(404, post("/api/v1/workspaces/" + missing + "/start", userToken(), null).status());
    assertEquals(404, post("/api/v1/workspaces/" + missing + "/stop", userToken(), null).status());
    assertEquals(
        404, post("/api/v1/workspaces/" + missing + "/restart", userToken(), null).status());
    assertEquals(404, get("/api/v1/workspaces/" + missing + "/metrics", userToken()).status());
  }
}
