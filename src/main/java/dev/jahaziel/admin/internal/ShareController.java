/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import dev.jahaziel.authorization.api.AccessDeniedException;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signed read-only share links. One hour TTL, revocable. Public redemption needs a one-line
 * permitAll for {@code /s/**} in SecurityConfig; until then redemption stays authenticated.
 */
@RestController
public class ShareController {
  private static final Duration TTL = Duration.ofHours(1);

  private final AuthorizationService auth;
  private final byte[] secret;
  private final Set<String> revoked = ConcurrentHashMap.newKeySet();

  public ShareController(
      AuthorizationService auth, @Value("${jahaziel.share.secret:}") String secret) {
    this.auth = Objects.requireNonNull(auth, "auth");
    if (secret == null || secret.isBlank()) {
      // ponytail: ephemeral secret; set jahaziel.share.secret so shares survive restarts.
      byte[] generated = new byte[32];
      new SecureRandom().nextBytes(generated);
      this.secret = generated;
    } else {
      this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }
  }

  /** Creates a share link for a workspace the caller may read. */
  @PostMapping("/api/v1/workspaces/{id}/shares")
  @ResponseStatus(HttpStatus.CREATED)
  public ShareResponse share(@PathVariable UUID id, java.security.Principal principal) {
    UserId user = userId(principal);
    WorkspaceId workspace = new WorkspaceId(id);
    if (!auth.isAllowed(user, "read", workspace)) {
      throw new AccessDeniedException(user, "read");
    }
    Instant expiresAt = Instant.now().plus(TTL);
    String token = token(workspace, expiresAt);
    return new ShareResponse("/s/" + token, token, expiresAt);
  }

  /** Verifies a share token. */
  @GetMapping("/s/{token}")
  public ShareInfo redeem(@PathVariable String token) {
    return verify(token);
  }

  /** Revokes a share token. */
  @DeleteMapping("/api/v1/shares")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@RequestParam String token, java.security.Principal principal) {
    ShareInfo info = verify(token);
    if (!auth.isAllowed(userId(principal), "read", new WorkspaceId(info.workspaceId()))) {
      throw new AccessDeniedException(userId(principal), "read");
    }
    revoked.add(token);
  }

  private ShareInfo verify(String token) {
    String[] parts = token.split("\\.");
    if (parts.length != 3 || revoked.contains(token)) {
      throw new IllegalArgumentException("invalid or revoked share link");
    }
    UUID workspaceId;
    long epoch;
    try {
      workspaceId = UUID.fromString(parts[0]);
      epoch = Long.parseLong(parts[1]);
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("invalid share link");
    }
    Instant expiresAt = Instant.ofEpochSecond(epoch);
    if (expiresAt.isBefore(Instant.now())) {
      throw new IllegalArgumentException("share link expired");
    }
    String payload = parts[0] + "." + parts[1];
    if (!sign(payload).equals(parts[2])) {
      throw new IllegalArgumentException("invalid share link");
    }
    return new ShareInfo(workspaceId, expiresAt);
  }

  private String token(WorkspaceId workspace, Instant expiresAt) {
    String payload = workspace.value() + "." + expiresAt.getEpochSecond();
    return payload + "." + sign(payload);
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static UserId userId(java.security.Principal principal) {
    try {
      return new UserId(UUID.fromString(principal.getName()));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("principal is not a user id");
    }
  }

  /** A created share link. */
  public record ShareResponse(String url, String token, Instant expiresAt) {}

  /** A verified share link. */
  public record ShareInfo(UUID workspaceId, Instant expiresAt) {}
}
