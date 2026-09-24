/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Admin dashboard, diagnostics download, and live log stream (non-demo auth branches). */
class AdminUiE2eTest extends E2eBase {
  @Test
  void overviewServesDashboardToMfaAdmin() throws Exception {
    E2eResponse page = getWithAccept("/_admin", adminToken(), "text/html");
    assertEquals(200, page.status(), page.body());
    assertTrue(page.header("content-type").contains("text/html"), page.header("content-type"));
    assertTrue(page.body().contains("Jahaziel admin"), page.body());
  }

  @Test
  void overviewRejectsPlainUser() throws Exception {
    // Errors render as JSON only, so a JSON client sees the 403; an HTML client
    // gets the Whitelabel fallback instead (no HTML error view is registered).
    E2eResponse page = get("/_admin", userToken());
    assertEquals(403, page.status(), page.body());
    assertTrue(page.body().contains("FORBIDDEN"), page.body());
  }

  @Test
  void overviewRejectsAnonymous() throws Exception {
    E2eResponse page = getAnonymous("/_admin");
    assertEquals(401, page.status(), page.body());
    assertTrue(page.body().contains("AUTH_REQUIRED"), page.body());
  }

  @Test
  void overviewRejectsAdminWithoutMfa() throws Exception {
    E2eResponse page = get("/_admin", nomfaAdminToken());
    assertEquals(403, page.status(), page.body());
  }

  @Test
  void diagnosticsDownload() throws Exception {
    E2eResponse bundle = getWithAccept("/_admin/diagnostics", adminToken(), "text/plain");
    assertEquals(200, bundle.status(), bundle.body());
    assertTrue(bundle.body().contains("status=ok"), bundle.body());
    assertTrue(
        bundle.allHeaders("content-disposition").contains("diagnostics.txt"),
        bundle.allHeaders("content-disposition"));
  }

  @Test
  void diagnosticsRejectsNonAdmin() throws Exception {
    assertEquals(403, get("/_admin/diagnostics", userToken()).status());
    assertEquals(401, getAnonymous("/_admin/diagnostics").status());
  }

  @Test
  void logsStreamServesEvents() throws Exception {
    E2eResponse stream = getWithAccept("/admin/v1/logs/stream", adminToken(), "text/event-stream");
    assertEquals(200, stream.status(), stream.body());
    assertTrue(
        stream.header("content-type").contains("text/event-stream"), stream.header("content-type"));
    assertTrue(stream.body().contains("data:"), stream.body());
  }

  @Test
  void logsStreamRejectsNonAdmin() throws Exception {
    assertEquals(403, get("/admin/v1/logs/stream", userToken()).status());
    assertEquals(401, getAnonymous("/admin/v1/logs/stream").status());
  }
}
