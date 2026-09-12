/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.api.JwtRejectedException;
import dev.jahaziel.identity.api.JwtSession;
import dev.jahaziel.identity.domain.UserId;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link JwtServiceImpl} with generated keys. */
class JwtServiceImplTest {
  private JwtServiceImpl service;
  private UserId userId;
  private UUID workspaceId;

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    service =
        new JwtServiceImpl(
            (RSAPrivateKey) pair.getPrivate(),
            (RSAPublicKey) pair.getPublic(),
            "https://code.jahaziel.dev",
            "jahaziel-code-server");
    userId = new UserId(UUID.randomUUID());
    workspaceId = UUID.randomUUID();
  }

  @Test
  void roundTripsSession() {
    String token =
        service.issue(userId, workspaceId, List.of("workspace:read"), Duration.ofMinutes(15));
    JwtSession session = service.verify(token);
    assertEquals(userId, session.user());
    assertEquals(workspaceId, session.workspaceId().orElseThrow());
    assertEquals(List.of("workspace:read"), session.scopes());
  }

  @Test
  void rejectsTamperedAndGarbage() {
    String token = service.issue(userId, workspaceId, List.of(), Duration.ofMinutes(15));
    assertThrows(JwtRejectedException.class, () -> service.verify(token + "x"));
    assertThrows(JwtRejectedException.class, () -> service.verify("not-a-token"));
  }

  @Test
  void rejectsExpired() {
    String token = service.issue(userId, null, List.of(), Duration.ofSeconds(-1));
    assertThrows(JwtRejectedException.class, () -> service.verify(token));
  }

  @Test
  void userOnlyTokenHasEmptyWorkspace() {
    String token = service.issue(userId, null, List.of(), Duration.ofMinutes(15));
    assertTrue(service.verify(token).workspaceId().isEmpty());
  }
}
