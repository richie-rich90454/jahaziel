/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal.devconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.JwtService;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.AuthPluginRegistry;
import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.identity.auth.wiring.AuthProviderConfig;
import dev.jahaziel.identity.auth.wiring.AuthProviderDispatcher;
import dev.jahaziel.identity.auth.wiring.UserIdentityStore;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.BffSessionEntity;
import dev.jahaziel.identity.persistence.BffSessionRepository;
import dev.jahaziel.identity.persistence.OidcPendingRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Backchannel logout ends every linked BFF session, and login refreshes basis-auth first. */
final class BasisAuthLogoutCleanupTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private BffSessionRepository sessions;
    private UserIdentityStore identities;
    private DevConnectServiceImpl service;

    @BeforeEach
    void setUp() {
        DevConnectProperties props = new DevConnectProperties();
        props.setAuthIssuer("https://idp.example.org");
        props.setRedirectUri("https://app.example.org/cb");
        props.setClientId("client");
        props.setClientSecret("secret");
        props.setResource("urn:example:api");
        sessions = mock(BffSessionRepository.class);
        identities = mock(UserIdentityStore.class);
        service =
                new DevConnectServiceImpl(
                        props,
                        mock(DevConnectClient.class),
                        mock(OidcPendingRepository.class),
                        sessions,
                        mock(AppUserRepository.class),
                        mock(UserRoleRepository.class),
                        mock(JwtService.class),
                        CLOCK,
                        null,
                        identities);
    }

    @Test
    void endsEverySessionForTheLinkedUserOnly() {
        UUID userId = UUID.randomUUID();
        when(identities.findUserId("basis-auth", "sub-1")).thenReturn(userId);
        BffSessionEntity mine = session("s1", userId);
        BffSessionEntity mineToo = session("s2", userId);
        BffSessionEntity other = session("s3", UUID.randomUUID());
        when(sessions.findAll()).thenReturn(Arrays.asList(mine, mineToo, other));

        service.endSessionsForSubject("basis-auth", "sub-1");

        verify(sessions).delete(mine);
        verify(sessions).delete(mineToo);
        verify(sessions, never()).delete(other);
    }

    @Test
    void unknownSubjectDeletesNothing() {
        when(identities.findUserId("basis-auth", "missing")).thenReturn(null);

        service.endSessionsForSubject("basis-auth", "missing");

        verify(sessions, never()).delete(any(BffSessionEntity.class));
    }

    @Test
    void basisAuthLoginRefreshesBeforeIssuingAnApiToken() {
        DevConnectProperties props = new DevConnectProperties();
        props.setAuthIssuer("https://idp.example.org");
        props.setRedirectUri("https://app.example.org/cb");
        props.setClientId("client");
        props.setClientSecret("secret");
        props.setResource("urn:example:api");
        AppUserRepository users = mock(AppUserRepository.class);
        UserRoleRepository roles = mock(UserRoleRepository.class);
        JwtService tokens = mock(JwtService.class);
        BffSessionRepository allSessions = mock(BffSessionRepository.class);
        when(users.save(any(AppUserEntity.class)))
                .thenAnswer(
                        new Answer<AppUserEntity>() {
                            @Override
                            public AppUserEntity answer(
                                    org.mockito.invocation.InvocationOnMock invocation) {
                                return invocation.getArgument(0);
                            }
                        });
        when(allSessions.save(any(BffSessionEntity.class)))
                .thenAnswer(
                        new Answer<BffSessionEntity>() {
                            @Override
                            public BffSessionEntity answer(
                                    org.mockito.invocation.InvocationOnMock invocation) {
                                return invocation.getArgument(0);
                            }
                        });
        when(tokens.issue(any(), any(), any(), any())).thenReturn("api-token");

        CountingBasisPlugin plugin = new CountingBasisPlugin();
        AuthPluginRegistry registry = new AuthPluginRegistry();
        registry.register(plugin);
        AuthProviderDispatcher dispatcher =
                new AuthProviderDispatcher(
                        registry,
                        new AuthProviderConfig(
                                Arrays.asList("basis-auth"), (PluginContext) null));
        DevConnectServiceImpl withDispatcher =
                new DevConnectServiceImpl(
                        props,
                        mock(DevConnectClient.class),
                        mock(OidcPendingRepository.class),
                        allSessions,
                        users,
                        roles,
                        tokens,
                        CLOCK,
                        dispatcher,
                        identities);

        withDispatcher.completeLogin("state-1", "code-1");

        assertEquals("plugin-session", plugin.refreshedSession());
    }

    private static BffSessionEntity session(String id, UUID userId) {
        BffSessionEntity session = new BffSessionEntity();
        session.setSessionId(id);
        session.setUserId(userId);
        return session;
    }

    private static final class CountingBasisPlugin extends AuthProviderPlugin {
        private String refreshed;

        private CountingBasisPlugin() {
            super(
                    new PluginDescriptor(
                            "basis-auth",
                            "1",
                            Collections.<String>emptyList(),
                            Collections.<String>emptyList()));
        }

        @Override
        public void initialize(PluginContext context) {}

        @Override
        public void shutdown() {}

        @Override
        public String startLogin(
                PluginContext context, String state, String nonce, String redirectUri) {
            return "https://idp.example.org/authorize";
        }

        @Override
        public AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters) {
            Map<String, String> claims = new HashMap<String, String>();
            claims.put("session_id", "plugin-session");
            return new AuthIdentity(
                    "basis-auth", "sub-1", "user@example.org", "User", true, true, claims);
        }

        @Override
        public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
            return null;
        }

        @Override
        public AuthIdentity refresh(PluginContext context, String sessionId) {
            refreshed = sessionId;
            return null;
        }

        @Override
        public void endSession(PluginContext context, String sessionId) {}

        private String refreshedSession() {
            return refreshed;
        }
    }
}
