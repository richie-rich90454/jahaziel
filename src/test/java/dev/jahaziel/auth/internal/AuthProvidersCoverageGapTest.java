/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.auth.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.JwtSession;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.spi.UserIdentity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthProvidersCoverageGapTest {
  @Test
  void devConnectDelegatesAndFinds() {
    JwtService tokens = mock(JwtService.class);
    UserService users = mock(UserService.class);
    DevConnectAuthProvider provider = new DevConnectAuthProvider(tokens, users);
    assertEquals("devconnect", provider.name());
    UserId id = new UserId(UUID.randomUUID());
    JwtSession session = mock(JwtSession.class);
    when(session.user()).thenReturn(id);
    when(tokens.verify("token")).thenReturn(session);
    UserIdentity identity = new UserIdentity("sub", "e@x.y", "N", List.of("ROLE_USER"), List.of());
    when(users.get(id)).thenReturn(identity);
    assertEquals(identity, provider.authenticate("token"));
    assertEquals(identity, provider.findById(id.value().toString()).orElseThrow());
    when(users.get(id)).thenThrow(new RuntimeException("gone"));
    assertTrue(provider.findById(id.value().toString()).isEmpty());
    assertTrue(provider.findById("not-a-uuid").isEmpty());
  }

  @Test
  void stubAcceptsFixedUser() {
    StubAuthProvider provider = new StubAuthProvider();
    assertEquals("stub", provider.name());
    UserIdentity authenticated = provider.authenticate("anything");
    assertEquals("stub-user", authenticated.externalId());
    assertEquals(authenticated, provider.findById("stub-user").orElseThrow());
    assertTrue(provider.findById("someone-else").isEmpty());
  }
}
