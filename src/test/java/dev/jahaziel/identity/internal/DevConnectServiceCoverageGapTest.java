/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.internal.devconnect.DevConnectClient;
import dev.jahaziel.identity.internal.devconnect.DevConnectProperties;
import dev.jahaziel.identity.internal.devconnect.DevConnectServiceImpl;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.BffSessionRepository;
import dev.jahaziel.identity.persistence.OidcPendingEntity;
import dev.jahaziel.identity.persistence.OidcPendingRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class DevConnectServiceCoverageGapTest {
  private DevConnectProperties configured() {
    DevConnectProperties p = new DevConnectProperties();
    p.setAuthIssuer("https://idp.example.com");
    p.setRedirectUri("https://app/cb");
    p.setClientId("c");
    p.setClientSecret("s");
    p.setResource("r");
    return p;
  }

  private OidcPendingEntity row(Clock clock) {
    OidcPendingEntity row = new OidcPendingEntity();
    row.setState("s");
    row.setNonce("n");
    row.setCodeVerifier("v");
    row.setRedirectUri("https://app/cb");
    row.setCreatedAt(clock.instant());
    row.setExpiresAt(clock.instant().plus(Duration.ofMinutes(5)));
    return row;
  }

  private DevConnectServiceImpl svc(
      DevConnectProperties props,
      DevConnectClient client,
      OidcPendingRepository pending,
      BffSessionRepository sessions,
      AppUserRepository users,
      UserRoleRepository roles,
      JwtService tokens,
      Clock clock) {
    return new DevConnectServiceImpl(props, client, pending, sessions, users, roles, tokens, clock);
  }

  @Test
  void provisionLinksBlankExternalId() {
    DevConnectProperties props = configured();
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    var pending = mock(OidcPendingRepository.class);
    var client = mock(DevConnectClient.class);
    var users = mock(AppUserRepository.class);
    var sessions = mock(BffSessionRepository.class);
    var tokens = mock(JwtService.class);
    when(tokens.issue(any(), any(), any(), any())).thenReturn("tok");
    when(users.save(any())).thenAnswer(i -> i.getArgument(0));
    when(pending.findById("s")).thenReturn(Optional.of(row(clock)));
    when(client.exchange(any(), any(), any())).thenReturn(new DevConnectClient.Tokens("id", "acc"));
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("sub");
    when(client.verifyIdToken(any(), any())).thenReturn(jwt);
    when(client.userinfo(any())).thenReturn(new DevConnectClient.UserInfo("sub", "e@x.y", "N"));
    when(users.findByExternalId("sub")).thenReturn(Optional.empty());
    AppUserEntity legacy = new AppUserEntity();
    legacy.setUserId(UUID.randomUUID());
    legacy.setStatus("active");
    legacy.setExternalId("  ");
    when(users.findByEmailIgnoreCase("e@x.y")).thenReturn(List.of(legacy));
    var s =
        svc(props, client, pending, sessions, users, mock(UserRoleRepository.class), tokens, clock);
    assertEquals(legacy.getUserId(), s.completeLogin("s", "c").userId());
    assertEquals("sub", legacy.getExternalId());
  }

  @Test
  void provisionTouchesAlreadyLinkedEmail() {
    DevConnectProperties props = configured();
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    var pending = mock(OidcPendingRepository.class);
    var client = mock(DevConnectClient.class);
    var users = mock(AppUserRepository.class);
    var sessions = mock(BffSessionRepository.class);
    var tokens = mock(JwtService.class);
    when(tokens.issue(any(), any(), any(), any())).thenReturn("tok");
    when(users.save(any())).thenAnswer(i -> i.getArgument(0));
    when(pending.findById("s")).thenReturn(Optional.of(row(clock)));
    when(client.exchange(any(), any(), any())).thenReturn(new DevConnectClient.Tokens("id", "acc"));
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("sub");
    when(client.verifyIdToken(any(), any())).thenReturn(jwt);
    when(client.userinfo(any())).thenReturn(new DevConnectClient.UserInfo("sub", "e@x.y", "N"));
    when(users.findByExternalId("sub")).thenReturn(Optional.empty());
    AppUserEntity same = new AppUserEntity();
    same.setUserId(UUID.randomUUID());
    same.setStatus("active");
    same.setExternalId("sub");
    when(users.findByEmailIgnoreCase("e@x.y")).thenReturn(List.of(same));
    var s =
        svc(props, client, pending, sessions, users, mock(UserRoleRepository.class), tokens, clock);
    assertEquals(same.getUserId(), s.completeLogin("s", "c").userId());
  }
}
