/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Signed read-only share links. Redemption stays authenticated (no permitAll for {@code /s/**}), so
 * every redeem here carries a bearer token.
 */
class ShareE2eTest extends E2eBase {
  @Test
  void createAndRedeemRoundTrip() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("shared"));

    E2eResponse created = post("/api/v1/workspaces/" + id + "/shares", userToken(), null);
    assertEquals(201, created.status(), created.body());
    String token = bodyJson(created).get("token").asString();
    assertTrue(!token.isBlank(), created.body());
    assertTrue(bodyJson(created).get("url").asString().startsWith("/s/"), created.body());

    E2eResponse redeemed = get("/s/" + token, userToken());
    assertEquals(200, redeemed.status(), redeemed.body());
    assertTrue(redeemed.body().contains("\"workspaceId\":\"" + id + "\""), redeemed.body());
  }

  @Test
  void anonymousRedeemIsUnauthorized() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("redonly"));
    String token =
        bodyJson(post("/api/v1/workspaces/" + id + "/shares", userToken(), null))
            .get("token")
            .asString();

    E2eResponse redeemed = getAnonymous("/s/" + token);
    assertEquals(401, redeemed.status(), redeemed.body());
  }

  @Test
  void revokeInvalidatesToken() throws Exception {
    String id = createWorkspace(userToken(), uniqueName("revoked"));
    String token =
        bodyJson(post("/api/v1/workspaces/" + id + "/shares", userToken(), null))
            .get("token")
            .asString();

    E2eResponse revoked = delete("/api/v1/shares?token=" + token, userToken());
    assertEquals(204, revoked.status(), revoked.body());

    E2eResponse redeemed = get("/s/" + token, userToken());
    assertEquals(400, redeemed.status(), redeemed.body());
    assertTrue(redeemed.body().contains("VALIDATION_FAILED"), redeemed.body());
  }

  @Test
  void garbageTokenIsRejected() throws Exception {
    E2eResponse redeemed = get("/s/nope", userToken());
    assertEquals(400, redeemed.status(), redeemed.body());
    assertTrue(redeemed.body().contains("VALIDATION_FAILED"), redeemed.body());
  }

  @Test
  void expiredTokenIsRejected() throws Exception {
    // Expiry is checked before the signature, so a past epoch fails without the secret.
    String id = createWorkspace(userToken(), uniqueName("stale"));
    long past = Instant.now().minus(Duration.ofHours(2)).getEpochSecond();
    String forged = id + "." + past + ".00";

    E2eResponse redeemed = get("/s/" + forged, userToken());
    assertEquals(400, redeemed.status(), redeemed.body());
    assertTrue(redeemed.body().contains("expired"), redeemed.body());
  }

  @Test
  void revokeUnknownTokenIsRejected() throws Exception {
    E2eResponse revoked = delete("/api/v1/shares?token=nope", userToken());
    assertEquals(400, revoked.status(), revoked.body());
  }

  @Test
  void shareUnknownWorkspaceIsNotFound() throws Exception {
    assertEquals(
        404,
        post("/api/v1/workspaces/" + UUID.randomUUID() + "/shares", userToken(), null).status());
  }

  @Test
  void nonOwnerCannotShare() throws Exception {
    String id = createWorkspace(adminToken(), uniqueName("theirs"));

    E2eResponse created = post("/api/v1/workspaces/" + id + "/shares", userToken(), null);
    assertEquals(403, created.status(), created.body());
    assertTrue(created.body().contains("\"code\":\"FORBIDDEN\""), created.body());
  }
}
