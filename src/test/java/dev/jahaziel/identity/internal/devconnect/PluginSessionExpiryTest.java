/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.wiring.AuthProviderDispatcher;
import dev.jahaziel.identity.auth.wiring.UserIdentityStore;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.BffSessionEntity;
import dev.jahaziel.identity.persistence.BffSessionRepository;
import dev.jahaziel.identity.persistence.OidcPendingRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

/** Plugin sessions have no store expiry, so activeUser owns the expiry boundary. */
final class PluginSessionExpiryTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private MutableClock clock;
    private BffSessionRepository sessions;
    private AppUserRepository users;
    private AuthProviderDispatcher dispatcher;
    private UserIdentityStore identities;
    private UUID userId;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        sessions = mock(BffSessionRepository.class);
        users = mock(AppUserRepository.class);
        dispatcher = mock(AuthProviderDispatcher.class);
        identities = mock(UserIdentityStore.class);
        userId = UUID.randomUUID();
        when(dispatcher.handles()).thenReturn(true);
        when(dispatcher.currentIdentity("s1")).thenReturn(identity());
        when(identities.findUserId("google", "sub-1")).thenReturn(userId);
        AppUserEntity user = new AppUserEntity();
        user.setUserId(userId);
        user.setStatus("active");
        when(users.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void expiredBffSessionNeverFallsBackToThePluginIdentity() {
        BffSessionEntity expired = new BffSessionEntity();
        expired.setSessionId("s1");
        expired.setUserId(userId);
        expired.setExpiresAt(START.minus(Duration.ofSeconds(1)));
        when(sessions.findById("s1")).thenReturn(Optional.of(expired));

        assertFalse(service().activeUser("s1").isPresent());
        verify(dispatcher, never()).currentIdentity("s1");
        verify(identities, never()).findUserId("google", "sub-1");
    }

    @Test
    void aMissingBffRowUsesThePluginIdentityOnlyForTheBoundedWindow() {
        when(sessions.findById("s1")).thenReturn(Optional.<BffSessionEntity>empty());
        DevConnectServiceImpl service = service();

        assertTrue(service.activeUser("s1").isPresent());
        assertEquals(1, service.activeUser("s1").isPresent() ? 1 : 0);
        clock.advance(Duration.ofMinutes(16L));

        assertFalse(service.activeUser("s1").isPresent());
        verify(dispatcher, times(1)).currentIdentity("s1");
    }

    private DevConnectServiceImpl service() {
        DevConnectProperties props = new DevConnectProperties();
        props.setAuthIssuer("https://idp.example.org");
        props.setRedirectUri("https://app.example/cb");
        props.setClientId("client");
        props.setClientSecret("secret");
        props.setResource("urn:example:api");
        return new DevConnectServiceImpl(
                props,
                mock(DevConnectClient.class),
                mock(OidcPendingRepository.class),
                sessions,
                users,
                mock(UserRoleRepository.class),
                mock(JwtService.class),
                clock,
                dispatcher,
                identities,
                Duration.ofMinutes(15L));
    }

    private static AuthIdentity identity() {
        return new AuthIdentity(
                "google",
                "sub-1",
                "user@example.org",
                "User",
                true,
                false,
                Collections.<String, String>unmodifiableMap(new HashMap<String, String>()));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        private void advance(Duration amount) {
            now = now.plus(amount);
        }
    }
}
