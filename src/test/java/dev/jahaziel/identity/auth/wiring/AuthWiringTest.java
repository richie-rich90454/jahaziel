/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jwt.JWTClaimsSet;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.identity.auth.basis.BackchannelHandler;
import dev.jahaziel.identity.auth.basis.BasisConfig;
import dev.jahaziel.identity.auth.basis.BasisHttp;
import dev.jahaziel.identity.auth.basis.BasisStore;
import dev.jahaziel.identity.auth.basis.TokenVerifier;
import dev.jahaziel.identity.auth.basis.TokenVerifier.VerifiedToken;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class AuthWiringTest {
    private static final String RESOURCE = "urn:example:api";

    @TempDir java.nio.file.Path temp;

    private PluginContext context;
    private BasisStore store;
    private TokenVerifier verifier;
    private BackchannelController controller;

    @BeforeEach
    void setUp() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("basis.auth-issuer", "https://idp.example.org");
        settings.put("basis.client-id", "example-web");
        settings.put("basis.resource", RESOURCE);
        settings.put("basis.resource-secret", "resource-secret-1234567890");
        context = new PluginContext(temp, settings);
        BasisConfig config = new BasisConfig(context);
        store = new BasisStore(config);
        verifier = mock(TokenVerifier.class);
        BackchannelHandler handler =
                new BackchannelHandler(config, mock(BasisHttp.class), verifier, store);
        context.putService(BasisAuthPlugin.SERVICE_BACKCHANNEL, handler);
        controller = new BackchannelController(context);
    }

    @Test
    void configReadsOrderedProvidersAndDeduplicates() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("auth.providers", " google , basis-auth , google , ");
        AuthProviderConfig config =
                AuthProviderConfig.fromSettings(new PluginContext(temp, settings));
        assertEquals(Arrays.asList("google", "basis-auth"), config.enabledProviders());
        assertFalse(config.isEmpty());

        AuthProviderConfig empty =
                AuthProviderConfig.fromSettings(
                        new PluginContext(temp, new HashMap<String, String>()));
        assertTrue(empty.isEmpty());
    }

    @Test
    void identityStoreReadsAndWritesProviderSubjectLinks() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UserIdentityStore identities = new UserIdentityStore(jdbc);
        UUID userId = UUID.randomUUID();

        when(jdbc.queryForList(anyString(), eq("google"), eq("sub-1")))
                .thenReturn(new ArrayList<Map<String, Object>>());
        assertNull(identities.findUserId("google", "sub-1"));

        Map<String, Object> row = new HashMap<String, Object>();
        row.put("user_id", userId);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        rows.add(row);
        when(jdbc.queryForList(anyString(), eq("google"), eq("sub-1"))).thenReturn(rows);
        assertEquals(userId, identities.findUserId("google", "sub-1"));

        identities.link(
                "google", "sub-1", userId, "user@example.org", "User", java.time.Instant.EPOCH);
        verify(jdbc)
                .update(
                        anyString(),
                        eq("google"),
                        eq("sub-1"),
                        eq(userId),
                        eq("user@example.org"),
                        eq("User"),
                        org.mockito.ArgumentMatchers.any(java.sql.Timestamp.class));
    }

    @Test
    void backchannelLogoutTokenEndsTheProviderSession() {
        store.putSession("session-1", identity("basis-auth", "sub-1"));
        assertNotNull(store.getSession("session-1"));

        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .claim("sub", "sub-1")
                        .claim(TokenVerifier.LOGOUT_EVENT, new HashMap<String, Object>())
                        .build();
        VerifiedToken verified = mock(VerifiedToken.class);
        when(verified.claims()).thenReturn(claims);
        when(verifier.verifyBackchannelToken("logout-jwt", "logout+jwt", RESOURCE))
                .thenReturn(verified);

        Map<String, String> form = new HashMap<String, String>();
        form.put("logout_token", "logout-jwt");
        ResponseEntity<String> response = controller.handle(form);

        assertEquals(204, response.getStatusCode().value());
        assertNull(store.getSession("session-1"));
    }

    @Test
    void backchannelRevocationDenylistsTheAccessToken() {
        long validAfter = System.currentTimeMillis() / 1000L - 5L;
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .claim("access_token_jti", "jti-1")
                        .claim("valid_after", Long.valueOf(validAfter))
                        .claim("access_token_expires_at", Long.valueOf(validAfter + 3600L))
                        .build();
        VerifiedToken verified = mock(VerifiedToken.class);
        when(verified.claims()).thenReturn(claims);
        when(verifier.verifyBackchannelToken("revoke-jwt", "at-revocation+jwt", RESOURCE))
                .thenReturn(verified);

        Map<String, String> form = new HashMap<String, String>();
        form.put("action", "revoke_access_token");
        form.put("jti", "jti-1");
        form.put("valid_after", Long.toString(validAfter));
        form.put("revocation_token", "revoke-jwt");
        ResponseEntity<String> response = controller.handle(form);

        assertEquals(204, response.getStatusCode().value());
        assertTrue(store.isDenied("jti-1", validAfter));
    }

    @Test
    void backchannelWithoutReceiverIsNotFound() {
        PluginContext empty = new PluginContext(temp, new HashMap<String, String>());
        ResponseEntity<String> response =
                new BackchannelController(empty).handle(new HashMap<String, String>());
        assertEquals(404, response.getStatusCode().value());
    }

    private static AuthIdentity identity(String provider, String subject) {
        return new AuthIdentity(
                provider,
                subject,
                "user@example.org",
                "Example User",
                true,
                false,
                new HashMap<String, String>());
    }
}
