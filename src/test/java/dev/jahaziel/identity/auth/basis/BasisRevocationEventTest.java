/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Revocation shape versus routine refresh replacement: what is signed and what it may end. */
final class BasisRevocationEventTest {
    private static final String REDIRECT = "https://app.example.org/oauth/callback";
    private static final String RESOURCE = "urn:example:api";
    private static final String CLIENT_ID = "example-web";
    private static final String SECRET = "client-secret-1234567890";

    @TempDir Path temp;

    private LoopbackIdp idp;
    private PluginContext context;
    private BasisAuthPlugin plugin;
    private BasisStore store;

    @BeforeEach
    void setUp() throws Exception {
        idp = LoopbackIdp.start(CLIENT_ID, SECRET, RESOURCE);
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("basis.auth-issuer", idp.issuer());
        settings.put("basis.client-id", CLIENT_ID);
        settings.put("basis.client-secret", SECRET);
        settings.put("basis.resource", RESOURCE);
        settings.put("basis.resource-secret", "resource-secret-1234567890");
        settings.put("basis.internal-url", idp.issuer());
        context = new PluginContext(temp, settings);
        plugin = new BasisAuthPlugin();
        plugin.initialize(context);
        store = (BasisStore) context.service(BasisAuthPlugin.SERVICE_STORE);
    }

    @AfterEach
    void tearDown() {
        plugin.shutdown();
        idp.close();
    }

    @Test
    void signedSubRevocationEndsSessionsAndDeniesJti() throws Exception {
        AuthIdentity identity = login("explicit-state", "explicit-nonce");
        String sessionId = identity.claims().get("session_id");
        String jti = "explicit-jti";
        long validAfter = System.currentTimeMillis() / 1000L - 5L;

        HttpResult result = revoke(jti, validAfter, identity.subject());

        assertEquals(204, result.status());
        assertEquals(identity.subject(), result.subject());
        assertTrue(store.isDenied(jti, System.currentTimeMillis() / 1000L));
        assertNull(plugin.currentIdentity(context, sessionId));
    }

    @Test
    void routineRefreshReplacementHasNoSubAndOnlyDeniesJti() throws Exception {
        AuthIdentity identity = login("refresh-state", "refresh-nonce");
        String sessionId = identity.claims().get("session_id");
        String jti = "refresh-jti";
        long validAfter = System.currentTimeMillis() / 1000L - 5L;

        // Exact claims issued by basis-auth keys.ts issueAccessTokenRevocation: no sub.
        HttpResult result = revoke(jti, validAfter, null);

        assertEquals(204, result.status());
        assertNull(result.subject());
        assertTrue(store.isDenied(jti, System.currentTimeMillis() / 1000L));
        assertNotNull(plugin.currentIdentity(context, sessionId));
    }

    @Test
    void refreshEventCarryingSignedSubKeepsCurrentSessionDeletionBehavior() throws Exception {
        AuthIdentity identity = login("signed-refresh-state", "signed-refresh-nonce");
        String sessionId = identity.claims().get("session_id");
        String jti = "signed-refresh-jti";
        long validAfter = System.currentTimeMillis() / 1000L - 5L;

        // No current issuer event supplies sub. This pins today's defensive behavior if a
        // future refresh event does: it ends the BFF session as well as denying the JTI.
        HttpResult result = revoke(jti, validAfter, identity.subject());

        assertEquals(204, result.status());
        assertEquals(identity.subject(), result.subject());
        assertTrue(store.isDenied(jti, System.currentTimeMillis() / 1000L));
        assertNull(plugin.currentIdentity(context, sessionId));
    }

    private HttpResult revoke(String jti, long validAfter, String subject) {
        Map<String, Object> claims = baseClaims();
        claims.put("access_token_jti", jti);
        claims.put("valid_after", Long.valueOf(validAfter));
        claims.put(
                "access_token_expires_at",
                Long.valueOf(System.currentTimeMillis() / 1000L + 3600L));
        if (subject != null) {
            claims.put("sub", subject);
        }
        Map<String, String> form = new HashMap<String, String>();
        form.put("action", "revoke_access_token");
        form.put("jti", jti);
        form.put("valid_after", Long.toString(validAfter));
        form.put("revocation_token", idp.sign("at-revocation+jwt", claims));
        return plugin.backchannel().handle(form);
    }

    private Map<String, Object> baseClaims() {
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put("iss", idp.issuer());
        claims.put("aud", RESOURCE);
        claims.put("iat", Long.valueOf(System.currentTimeMillis() / 1000L));
        claims.put("exp", Long.valueOf(System.currentTimeMillis() / 1000L + 120L));
        return claims;
    }

    private AuthIdentity login(String state, String nonce) throws Exception {
        String url = plugin.startLogin(context, state, nonce, REDIRECT);
        String code = idp.authorize(url);
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", state);
        parameters.put("code", code);
        return plugin.handleCallback(context, parameters);
    }
}
