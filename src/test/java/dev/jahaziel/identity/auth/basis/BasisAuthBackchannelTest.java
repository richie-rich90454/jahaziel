/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class BasisAuthBackchannelTest {
    private static final String REDIRECT = "https://app.example.org/oauth/callback";
    private static final String RESOURCE = "urn:example:api";
    private static final String CLIENT_ID = "example-web";
    private static final String SECRET = "client-secret-1234567890";
    private static final String RESOURCE_SECRET = "resource-secret-1234567890";

    @TempDir java.nio.file.Path temp;

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
        settings.put("basis.resource-secret", RESOURCE_SECRET);
        settings.put("basis.internal-url", idp.issuer());
        context = new PluginContext(temp, settings);
        plugin = new BasisAuthPlugin();
        plugin.initialize(context);
        store = (BasisStore) context.service(BasisAuthPlugin.SERVICE_STORE);
    }

    @AfterEach
    void tearDown() {
        idp.close();
    }

    @Test
    void testActionReturns202AndPostsHmacProof() {
        Map<String, String> form = new HashMap<String, String>();
        form.put("action", "test");
        form.put("nonce", "nonce-abc");
        form.put("challenge", "challenge-xyz");

        HttpResult result = plugin.backchannel().handle(form);

        assertEquals(202, result.status());
        assertNotNull(idp.proofBody());
        assertEquals("nonce-abc", idp.proofBody().get("nonce").asString());
        assertEquals(
                hmac(RESOURCE_SECRET, "challenge-xyz"),
                idp.proofBody().get("challenge_hash").asString());
        assertEquals("Bearer " + RESOURCE_SECRET, idp.proofAuthorization());
        assertEquals(BasisHttp.encode(RESOURCE), idp.proofResource());
    }

    @Test
    void logoutTokenEndsSessionsForSubject() throws Exception {
        AuthIdentity identity = login("logout-state", "logout-nonce");
        String sessionId = identity.claims().get("session_id");
        assertNotNull(plugin.currentIdentity(context, sessionId));

        Map<String, Object> claims = baseClaims(RESOURCE);
        claims.put("sub", "basis-sub-1");
        claims.put(TokenVerifier.LOGOUT_EVENT, new HashMap<String, Object>());
        Map<String, String> form = new HashMap<String, String>();
        form.put("logout_token", idp.sign("logout+jwt", claims));

        HttpResult result = plugin.backchannel().handle(form);

        assertEquals(204, result.status());
        assertNull(plugin.currentIdentity(context, sessionId));
    }

    @Test
    void logoutTokenWithoutBackchannelEventIsRejected() {
        Map<String, Object> claims = baseClaims(RESOURCE);
        claims.put("sub", "basis-sub-1");
        Map<String, String> form = new HashMap<String, String>();
        form.put("logout_token", idp.sign("logout+jwt", claims));
        assertThrows(BasisException.class, new ThrowingRun(form));
    }

    @Test
    void revocationDenylistsJtiAndHonoursEarliestValidAfter() {
        String jti = "revoked-jti-1";
        String access = idp.accessToken(jti);
        assertEquals(200, plugin.backchannel().validateBearer("Bearer " + access).status());

        long first = System.currentTimeMillis() / 1000L - 5L;
        revoke(jti, first);
        assertEquals(first, store.validAfter(jti));
        assertEquals(401, plugin.backchannel().validateBearer("Bearer " + access).status());

        long later = System.currentTimeMillis() / 1000L + 100L;
        revoke(jti, later);
        assertEquals(first, store.validAfter(jti));

        long earlier = first - 10L;
        revoke(jti, earlier);
        assertEquals(earlier, store.validAfter(jti));
    }

    @Test
    void revocationWithMismatchedJtiIsRejected() {
        long validAfter = System.currentTimeMillis() / 1000L;
        Map<String, Object> claims = baseClaims(RESOURCE);
        claims.put("access_token_jti", "another-jti");
        claims.put("valid_after", Long.valueOf(validAfter));
        Map<String, String> form = new HashMap<String, String>();
        form.put("action", "revoke_access_token");
        form.put("jti", "the-jti");
        form.put("valid_after", Long.toString(validAfter));
        form.put("revocation_token", idp.sign("at-revocation+jwt", claims));
        assertThrows(BasisException.class, new ThrowingRun(form));
    }

    @Test
    void invalidBearerAlwaysGetsTheStandardRejection() {
        HttpResult garbage = plugin.backchannel().validateBearer("Bearer not-a-token");
        assertEquals(401, garbage.status());
        assertEquals("Bearer error=\"invalid_token\"", garbage.headers().get("WWW-Authenticate"));
        assertEquals("application/json", garbage.headers().get("Content-Type"));
        assertTrue(garbage.body().contains("\"error\":\"invalid_token\""));
        assertTrue(garbage.body().contains("Invalid access token"));
        assertFalse(garbage.body().contains("not-a-token"));

        assertEquals(401, plugin.backchannel().validateBearer(null).status());
        assertEquals(401, plugin.backchannel().validateBearer("Basic abc").status());
    }

    private void revoke(String jti, long validAfter) {
        Map<String, Object> claims = baseClaims(RESOURCE);
        claims.put("access_token_jti", jti);
        claims.put("valid_after", Long.valueOf(validAfter));
        claims.put(
                "access_token_expires_at",
                Long.valueOf(System.currentTimeMillis() / 1000L + 3600L));
        Map<String, String> form = new HashMap<String, String>();
        form.put("action", "revoke_access_token");
        form.put("jti", jti);
        form.put("valid_after", Long.toString(validAfter));
        form.put("revocation_token", idp.sign("at-revocation+jwt", claims));
        assertEquals(204, plugin.backchannel().handle(form).status());
    }

    private Map<String, Object> baseClaims(String audience) {
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put("iss", idp.issuer());
        claims.put("aud", audience);
        claims.put("exp", Long.valueOf(System.currentTimeMillis() / 1000L + 120L));
        claims.put("iat", Long.valueOf(System.currentTimeMillis() / 1000L));
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

    private static String hmac(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[digest.length * 2];
            for (int index = 0; index < digest.length; index++) {
                int value = digest[index] & 0xff;
                hex[index * 2] = Character.forDigit(value >>> 4, 16);
                hex[index * 2 + 1] = Character.forDigit(value & 0x0f, 16);
            }
            return new String(hex);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private final class ThrowingRun implements org.junit.jupiter.api.function.Executable {
        private final Map<String, String> form;

        private ThrowingRun(Map<String, String> form) {
            this.form = form;
        }

        @Override
        public void execute() {
            plugin.backchannel().handle(form);
            fail("expected the token to be rejected");
        }
    }
}
