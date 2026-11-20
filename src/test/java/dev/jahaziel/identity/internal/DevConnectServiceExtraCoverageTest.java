/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.DevConnectException;
import dev.jahaziel.identity.api.DevConnectService;
import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.api.LoginRejectedException;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.internal.devconnect.DevConnectClient;
import dev.jahaziel.identity.internal.devconnect.DevConnectProperties;
import dev.jahaziel.identity.internal.devconnect.DevConnectServiceImpl;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.BffSessionEntity;
import dev.jahaziel.identity.persistence.BffSessionRepository;
import dev.jahaziel.identity.persistence.OidcPendingEntity;
import dev.jahaziel.identity.persistence.OidcPendingRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class DevConnectServiceExtraCoverageTest {
    private DevConnectProperties configured() {
        DevConnectProperties p = new DevConnectProperties();
        p.setAuthIssuer("https://idp.example.com");
        p.setRedirectUri("https://app/cb");
        p.setClientId("c");
        p.setClientSecret("s");
        p.setResource("r");
        return p;
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
        return new DevConnectServiceImpl(
                props, client, pending, sessions, users, roles, tokens, clock);
    }

    private static Answer<Object> firstArgument() {
        return new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                return invocation.getArgument(0);
            }
        };
    }

    private static Executable complete(
            final DevConnectServiceImpl s, final String state, final String code) {
        return new Executable() {
            @Override
            public void execute() {
                s.completeLogin(state, code);
            }
        };
    }

    private static OidcPendingEntity pendingRow(Clock clock) {
        OidcPendingEntity row = new OidcPendingEntity();
        row.setState("s");
        row.setNonce("n");
        row.setCodeVerifier("v");
        row.setRedirectUri("https://app/cb");
        row.setCreatedAt(clock.instant());
        row.setExpiresAt(clock.instant().plus(Duration.ofMinutes(5)));
        return row;
    }

    @Test
    void startRequiresConfig() {
        DevConnectProperties empty = new DevConnectProperties();
        final DevConnectServiceImpl svc =
                svc(
                        empty,
                        mock(DevConnectClient.class),
                        mock(OidcPendingRepository.class),
                        mock(BffSessionRepository.class),
                        mock(AppUserRepository.class),
                        mock(UserRoleRepository.class),
                        mock(JwtService.class),
                        Clock.systemUTC());
        assertThrows(
                DevConnectException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        svc.startLogin();
                    }
                });
        assertThrows(DevConnectException.class, complete(svc, "s", "c"));
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new DevConnectServiceImpl(
                                null,
                                mock(DevConnectClient.class),
                                mock(OidcPendingRepository.class),
                                mock(BffSessionRepository.class),
                                mock(AppUserRepository.class),
                                mock(UserRoleRepository.class),
                                mock(JwtService.class),
                                Clock.systemUTC());
                    }
                });
    }

    @Test
    void completeRejectsBlankAndUnknownAndExpired() {
        DevConnectProperties props = configured();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        OidcPendingRepository pending = mock(OidcPendingRepository.class);
        final DevConnectServiceImpl svc =
                svc(
                        props,
                        mock(DevConnectClient.class),
                        pending,
                        mock(BffSessionRepository.class),
                        mock(AppUserRepository.class),
                        mock(UserRoleRepository.class),
                        mock(JwtService.class),
                        clock);
        assertThrows(LoginRejectedException.class, complete(svc, null, "c"));
        assertThrows(LoginRejectedException.class, complete(svc, " ", "c"));
        assertThrows(LoginRejectedException.class, complete(svc, "s", null));
        assertThrows(LoginRejectedException.class, complete(svc, "s", " "));
        when(pending.findById("s")).thenReturn(Optional.empty());
        assertThrows(LoginRejectedException.class, complete(svc, "s", "c"));
        OidcPendingEntity row = pendingRow(clock);
        row.setExpiresAt(clock.instant().minus(Duration.ofMinutes(1)));
        when(pending.findById("s")).thenReturn(Optional.of(row));
        assertThrows(LoginRejectedException.class, complete(svc, "s", "c"));
    }

    @Test
    void completeRejectsSubjectMismatch() {
        DevConnectProperties props = configured();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        OidcPendingRepository pending = mock(OidcPendingRepository.class);
        DevConnectClient client = mock(DevConnectClient.class);
        when(pending.findById("s")).thenReturn(Optional.of(pendingRow(clock)));
        when(client.exchange("c", "v", "https://app/cb"))
                .thenReturn(new DevConnectClient.Tokens("id", "acc"));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(null);
        when(client.verifyIdToken("id", "n")).thenReturn(jwt);
        when(client.userinfo("acc")).thenReturn(new DevConnectClient.UserInfo("sub", "e@x.y", "N"));
        final DevConnectServiceImpl svc =
                svc(
                        props,
                        client,
                        pending,
                        mock(BffSessionRepository.class),
                        mock(AppUserRepository.class),
                        mock(UserRoleRepository.class),
                        mock(JwtService.class),
                        clock);
        assertThrows(LoginRejectedException.class, complete(svc, "s", "c"));
        when(jwt.getSubject()).thenReturn("other");
        assertThrows(LoginRejectedException.class, complete(svc, "s", "c"));
    }

    @Test
    void provisionMultipleAccountsRejected() {
        DevConnectProperties props = configured();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        OidcPendingRepository pending = mock(OidcPendingRepository.class);
        DevConnectClient client = mock(DevConnectClient.class);
        AppUserRepository users = mock(AppUserRepository.class);
        when(pending.findById("s")).thenReturn(Optional.of(pendingRow(clock)));
        when(client.exchange(any(), any(), any()))
                .thenReturn(new DevConnectClient.Tokens("id", "acc"));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("sub");
        when(client.verifyIdToken(any(), any())).thenReturn(jwt);
        when(client.userinfo(any())).thenReturn(new DevConnectClient.UserInfo("sub", "e@x.y", "N"));
        when(users.findByExternalId("sub")).thenReturn(Optional.empty());
        AppUserEntity a = new AppUserEntity();
        a.setUserId(UUID.randomUUID());
        AppUserEntity b = new AppUserEntity();
        b.setUserId(UUID.randomUUID());
        when(users.findByEmailIgnoreCase("e@x.y")).thenReturn(List.of(a, b));
        final DevConnectServiceImpl svc =
                svc(
                        props,
                        client,
                        pending,
                        mock(BffSessionRepository.class),
                        users,
                        mock(UserRoleRepository.class),
                        mock(JwtService.class),
                        clock);
        assertThrows(LoginRejectedException.class, complete(svc, "s", "c"));
    }

    @Test
    void provisionLinksLegacyAndTouchesLinked() {
        DevConnectProperties props = configured();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        OidcPendingRepository pending = mock(OidcPendingRepository.class);
        DevConnectClient client = mock(DevConnectClient.class);
        AppUserRepository users = mock(AppUserRepository.class);
        BffSessionRepository sessions = mock(BffSessionRepository.class);
        JwtService tokens = mock(JwtService.class);
        when(tokens.issue(any(), any(), any(), any())).thenReturn("tok");
        when(users.save(any())).thenAnswer(firstArgument());
        when(pending.findById("s")).thenReturn(Optional.of(pendingRow(clock)));
        when(client.exchange(any(), any(), any()))
                .thenReturn(new DevConnectClient.Tokens("id", "acc"));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("sub");
        when(client.verifyIdToken(any(), any())).thenReturn(jwt);
        when(client.userinfo(any())).thenReturn(new DevConnectClient.UserInfo("sub", "e@x.y", "N"));
        AppUserEntity linked = new AppUserEntity();
        linked.setUserId(UUID.randomUUID());
        linked.setStatus("active");
        linked.setExternalId("sub");
        when(users.findByExternalId("sub")).thenReturn(Optional.of(linked));
        DevConnectServiceImpl svc =
                svc(
                        props,
                        client,
                        pending,
                        sessions,
                        users,
                        mock(UserRoleRepository.class),
                        tokens,
                        clock);
        DevConnectService.LoginResult result = svc.completeLogin("s", "c");
        assertEquals(linked.getUserId(), result.userId());
        AppUserEntity legacy = new AppUserEntity();
        legacy.setUserId(UUID.randomUUID());
        legacy.setStatus("active");
        legacy.setExternalId(null);
        when(users.findByExternalId("sub")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("e@x.y")).thenReturn(List.of(legacy));
        DevConnectService.LoginResult result2 = svc.completeLogin("s", "c");
        assertEquals(legacy.getUserId(), result2.userId());
        assertEquals("sub", legacy.getExternalId());
        AppUserEntity suspended = new AppUserEntity();
        suspended.setUserId(UUID.randomUUID());
        suspended.setStatus("suspended");
        suspended.setExternalId("sub");
        when(users.findByExternalId("sub")).thenReturn(Optional.of(suspended));
        assertThrows(LoginRejectedException.class, complete(svc, "s", "c"));
        AppUserEntity suspendedLegacy = new AppUserEntity();
        suspendedLegacy.setUserId(UUID.randomUUID());
        suspendedLegacy.setStatus("suspended");
        when(users.findByExternalId("sub")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("e@x.y")).thenReturn(List.of(suspendedLegacy));
        assertThrows(LoginRejectedException.class, complete(svc, "s", "c"));
    }

    @Test
    void provisionRejectsDifferentlyLinkedEmail() {
        DevConnectProperties props = configured();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        OidcPendingRepository pending = mock(OidcPendingRepository.class);
        DevConnectClient client = mock(DevConnectClient.class);
        AppUserRepository users = mock(AppUserRepository.class);
        when(pending.findById("s")).thenReturn(Optional.of(pendingRow(clock)));
        when(client.exchange(any(), any(), any()))
                .thenReturn(new DevConnectClient.Tokens("id", "acc"));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("sub-new");
        when(client.verifyIdToken(any(), any())).thenReturn(jwt);
        when(client.userinfo(any()))
                .thenReturn(new DevConnectClient.UserInfo("sub-new", "e@x.y", "N"));
        when(users.findByExternalId("sub-new")).thenReturn(Optional.empty());
        AppUserEntity other = new AppUserEntity();
        other.setUserId(UUID.randomUUID());
        other.setStatus("active");
        other.setExternalId("sub-old");
        when(users.findByEmailIgnoreCase("e@x.y")).thenReturn(List.of(other));
        final DevConnectServiceImpl svc =
                svc(
                        props,
                        client,
                        pending,
                        mock(BffSessionRepository.class),
                        users,
                        mock(UserRoleRepository.class),
                        mock(JwtService.class),
                        clock);
        assertThrows(LoginRejectedException.class, complete(svc, "s", "c"));
    }

    @Test
    void activeUserAndLogoutBranches() {
        DevConnectProperties props = configured();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        BffSessionRepository sessions = mock(BffSessionRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        DevConnectServiceImpl svc =
                svc(
                        props,
                        mock(DevConnectClient.class),
                        mock(OidcPendingRepository.class),
                        sessions,
                        users,
                        mock(UserRoleRepository.class),
                        mock(JwtService.class),
                        clock);
        assertTrue(svc.activeUser(null).isEmpty());
        assertTrue(svc.activeUser(" ").isEmpty());
        assertTrue(svc.activeUser("").isEmpty());
        when(sessions.findById("missing")).thenReturn(Optional.empty());
        assertTrue(svc.activeUser("missing").isEmpty());
        BffSessionEntity expired = new BffSessionEntity();
        expired.setSessionId("e");
        UUID uid = UUID.randomUUID();
        expired.setUserId(uid);
        expired.setExpiresAt(clock.instant().minus(Duration.ofMinutes(1)));
        when(sessions.findById("e")).thenReturn(Optional.of(expired));
        assertTrue(svc.activeUser("e").isEmpty());
        BffSessionEntity ok = new BffSessionEntity();
        ok.setSessionId("ok");
        ok.setUserId(uid);
        ok.setExpiresAt(clock.instant().plus(Duration.ofMinutes(5)));
        when(sessions.findById("ok")).thenReturn(Optional.of(ok));
        when(users.findById(uid)).thenReturn(Optional.empty());
        assertTrue(svc.activeUser("ok").isEmpty());
        AppUserEntity suspended = new AppUserEntity();
        suspended.setUserId(uid);
        suspended.setStatus("suspended");
        when(users.findById(uid)).thenReturn(Optional.of(suspended));
        assertTrue(svc.activeUser("ok").isEmpty());
        AppUserEntity active = new AppUserEntity();
        active.setUserId(uid);
        active.setStatus("active");
        when(users.findById(uid)).thenReturn(Optional.of(active));
        assertEquals(new UserId(uid), svc.activeUser("ok").orElseThrow());
        svc.logout(null);
        svc.logout(" ");
        svc.logout("");
        when(sessions.findById("gone")).thenReturn(Optional.empty());
        svc.logout("gone");
        when(sessions.findById("here")).thenReturn(Optional.of(ok));
        svc.logout("here");
        verify(sessions).delete(ok);
        assertEquals(props.getSessionTtl(), svc.sessionTtl());
        assertEquals(props.getTokenTtl(), svc.tokenTtl());
        assertTrue(svc.enabled() == props.configured());
    }
}
