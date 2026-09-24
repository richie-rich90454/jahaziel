/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.UUID;
import javax.crypto.Mac;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ShareCoverageGapTest {
  private AuthorizationService allowAll() {
    var auth = mock(AuthorizationService.class);
    when(auth.isAllowed(any(), eq("read"), any())).thenReturn(true);
    return auth;
  }

  private AuthorizationService denyAll() {
    var auth = mock(AuthorizationService.class);
    when(auth.isAllowed(any(), eq("read"), any())).thenReturn(false);
    return auth;
  }

  private Principal principal(UUID id) {
    return () -> id.toString();
  }

  @Test
  void explicitSecretRoundTrip() {
    var controller = new ShareController(allowAll(), "s3cret");
    UUID user = UUID.randomUUID();
    UUID workspace = UUID.randomUUID();
    var response = controller.share(workspace, principal(user));
    assertTrue(response.url().endsWith(response.token()));
    assertEquals(workspace, controller.redeem(response.token()).workspaceId());
  }

  @Test
  void blankSecretsGenerateEphemeral() {
    for (String secret : new String[] {null, "", "   "}) {
      var controller = new ShareController(allowAll(), secret);
      UUID user = UUID.randomUUID();
      UUID workspace = UUID.randomUUID();
      var response = controller.share(workspace, principal(user));
      assertEquals(workspace, controller.redeem(response.token()).workspaceId());
    }
  }

  @Test
  void shareDenied() {
    var controller = new ShareController(denyAll(), "s3cret");
    assertThrows(
        AccessDeniedException.class,
        () -> controller.share(UUID.randomUUID(), principal(UUID.randomUUID())));
  }

  @Test
  void shareRejectsNonUuidPrincipal() {
    var controller = new ShareController(allowAll(), "s3cret");
    assertThrows(
        IllegalArgumentException.class, () -> controller.share(UUID.randomUUID(), () -> "nope"));
  }

  @Test
  void redeemRejectsMalformed() {
    var controller = new ShareController(allowAll(), "s3cret");
    assertThrows(IllegalArgumentException.class, () -> controller.redeem("nope"));
    assertThrows(IllegalArgumentException.class, () -> controller.redeem("a.b"));
  }

  @Test
  void redeemRejectsBadPayload() {
    var controller = new ShareController(allowAll(), "s3cret");
    assertThrows(
        IllegalArgumentException.class,
        () -> controller.redeem(UUID.randomUUID() + ".notanumber.00"));
    assertThrows(IllegalArgumentException.class, () -> controller.redeem("not-a-uuid.123.00"));
  }

  @Test
  void redeemRejectsTamperedSignature() {
    var controller = new ShareController(allowAll(), "s3cret");
    String token = controller.share(UUID.randomUUID(), principal(UUID.randomUUID())).token();
    char last = token.charAt(token.length() - 1);
    String tampered = token.substring(0, token.length() - 1) + (last == '0' ? '1' : '0');
    assertThrows(IllegalArgumentException.class, () -> controller.redeem(tampered));
  }

  @Test
  void revokeRoundTrip() {
    var creator = new ShareController(allowAll(), "s3cret");
    String token = creator.share(UUID.randomUUID(), principal(UUID.randomUUID())).token();
    var revoker = new ShareController(allowAll(), "s3cret");
    revoker.revoke(token, principal(UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class, () -> revoker.redeem(token));
  }

  @Test
  void revokeDeniedAndBogus() {
    var creator = new ShareController(allowAll(), "s3cret");
    String token = creator.share(UUID.randomUUID(), principal(UUID.randomUUID())).token();
    var denier = new ShareController(denyAll(), "s3cret");
    assertThrows(
        AccessDeniedException.class, () -> denier.revoke(token, principal(UUID.randomUUID())));
    assertThrows(
        IllegalArgumentException.class, () -> denier.revoke("bogus", principal(UUID.randomUUID())));
  }

  @Test
  void signFailureSurfacesAsIllegalState() {
    var controller = new ShareController(allowAll(), "s3cret");
    try (var mac = Mockito.mockStatic(Mac.class)) {
      mac.when(() -> Mac.getInstance(anyString())).thenThrow(new NoSuchAlgorithmException("boom"));
      assertThrows(
          IllegalStateException.class,
          () -> controller.share(UUID.randomUUID(), principal(UUID.randomUUID())));
    }
  }
}
