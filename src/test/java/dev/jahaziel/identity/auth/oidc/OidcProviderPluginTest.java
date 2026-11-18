/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.oidc;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.GenericOidcPlugin;
import dev.jahaziel.plugin.PluginContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hermetic coverage of the shared authorization-code flow. */
final class OidcProviderPluginTest {
    @TempDir
    Path tempDir;

    private StubIdp idp;
    private GenericOidcPlugin plugin;
    private PluginContext context;

    @BeforeEach
    void setUp() {
        idp = new StubIdp();
        plugin = new GenericOidcPlugin();
        context = new PluginContext(tempDir, settings("oidc"));
        plugin.initialize(context);
    }

    @AfterEach
    void tearDown() {
        idp.stop();
    }

    @Test
    void authorizeUrlContainsRequiredParameters() {
        String url = plugin.startLogin(context, "state-a", "nonce-a", "http://app.test/callback");
        Map<String, String> query = query(url);
        assertEquals(idp.authorizationEndpoint(), url.substring(0, url.indexOf('?')));
        assertEquals("code", query.get("response_type"));
        assertEquals(StubIdp.CLIENT_ID, query.get("client_id"));
        assertEquals("http://app.test/callback", query.get("redirect_uri"));
        assertEquals("openid profile email groups", query.get("scope"));
        assertEquals("state-a", query.get("state"));
        assertEquals("nonce-a", query.get("nonce"));
        assertEquals("S256", query.get("code_challenge_method"));
        assertTrue(query.get("code_challenge").length() > 20);
    }

    @Test
    void unknownStateRejected() {
        assertRejected("state mismatch", params("never-issued", "code"));
    }

    @Test
    void consumedStateCannotBeReused() {
        plugin.startLogin(context, "state-reuse", "nonce-reuse", "http://app.test/callback");
        idp.setNextClaims(claims("nonce-reuse", ""));
        assertNotNull(plugin.handleCallback(context, params("state-reuse", "code-1")));
        assertRejected("state mismatch", params("state-reuse", "code-2"));
    }

    @Test
    void nonceMismatchRejected() {
        plugin.startLogin(context, "state-nonce", "nonce-expected", "http://app.test/callback");
        idp.setNextClaims(claims("nonce-wrong", ""));
        assertRejected("nonce mismatch", params("state-nonce", "code"));
    }

    @Test
    void codeExchangeUsesBasicAuthAndDerivesIdentity() {
        String url = plugin.startLogin(context, "state-exchange", "nonce-exchange", "http://app.test/callback");
        String challenge = query(url).get("code_challenge");
        idp.setNextClaims(claims("nonce-exchange", ",\"amr\":[\"pwd\",\"otp\"]"));
        AuthIdentity identity = plugin.handleCallback(context, params("state-exchange", "code-42"));

        String expectedBasic = "Basic " + Base64.getEncoder()
                .encodeToString("test-client:test-secret".getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedBasic, idp.lastAuthorization());
        assertEquals("authorization_code", idp.lastForm().get("grant_type"));
        assertEquals("code-42", idp.lastForm().get("code"));
        assertEquals("http://app.test/callback", idp.lastForm().get("redirect_uri"));
        String verifier = idp.lastForm().get("code_verifier");
        assertNotNull(verifier);
        assertEquals(challenge, StubIdp.challenge(verifier));

        assertEquals("oidc", identity.provider());
        assertEquals("user-123", identity.subject());
        assertEquals("oidc:user-123", identity.linkKey());
        assertEquals("user@example.test", identity.email());
        assertEquals("Test User", identity.displayName());
        assertTrue(identity.emailVerified());
        assertTrue(identity.mfaAssured());
        assertEquals("pwd otp", identity.claims().get("amr"));

        String sessionId = identity.claims().get("session_id");
        assertNotNull(sessionId);
        assertEquals(identity.subject(), plugin.currentIdentity(context, sessionId).subject());
        plugin.endSession(context, sessionId);
        assertNull(plugin.currentIdentity(context, sessionId));
    }

    @Test
    void mfaExtractedFromAcrAndAmr() {
        plugin.startLogin(context, "state-acr", "nonce-acr", "http://app.test/callback");
        idp.setNextClaims(claims("nonce-acr", ",\"acr\":\"urn:example:mfa\""));
        assertTrue(plugin.handleCallback(context, params("state-acr", "code")).mfaAssured());

        plugin.startLogin(context, "state-plain", "nonce-plain", "http://app.test/callback");
        idp.setNextClaims(claims("nonce-plain", ",\"amr\":[\"pwd\"]"));
        assertFalse(plugin.handleCallback(context, params("state-plain", "code")).mfaAssured());
    }

    @Test
    void publicClientSendsClientIdWithoutBasicAuth() {
        Map<String, String> settings = settings("oidc");
        settings.remove("oidc.client-secret");
        GenericOidcPlugin publicPlugin = new GenericOidcPlugin();
        PluginContext publicContext = new PluginContext(tempDir, settings);
        publicPlugin.initialize(publicContext);
        publicPlugin.startLogin(publicContext, "state-public", "nonce-public", "http://app.test/callback");
        idp.setNextClaims(claims("nonce-public", ""));
        publicPlugin.handleCallback(publicContext, params("state-public", "code"));
        assertNull(idp.lastAuthorization());
        assertEquals(StubIdp.CLIENT_ID, idp.lastForm().get("client_id"));
        assertFalse(idp.lastForm().containsKey("client_secret"));
    }

    @Test
    void atHashMismatchRejected() {
        plugin.startLogin(context, "state-hash", "nonce-hash", "http://app.test/callback");
        idp.setNextClaims(claims("nonce-hash", ",\"at_hash\":\"" + StubIdp.atHash("wrong-token") + "\""));
        assertRejected("access token hash mismatch", params("state-hash", "code"));
    }

    @Test
    void validAtHashAccepted() {
        plugin.startLogin(context, "state-hash-ok", "nonce-hash-ok", "http://app.test/callback");
        idp.setNextClaims(claims("nonce-hash-ok",
                ",\"at_hash\":\"" + StubIdp.atHash(StubIdp.ACCESS_TOKEN) + "\""));
        assertNotNull(plugin.handleCallback(context, params("state-hash-ok", "code")));
    }

    private void assertRejected(String expectedMessage, Map<String, String> parameters) {
        OidcException failure = assertThrows(OidcException.class, new Executable() {
            @Override
            public void execute() {
                plugin.handleCallback(context, parameters);
            }
        });
        assertTrue(failure.getMessage().contains(expectedMessage), failure.getMessage());
    }

    private Map<String, String> settings(String prefix) {
        Map<String, String> values = new HashMap<String, String>();
        values.put(prefix + ".issuer", idp.issuer());
        values.put(prefix + ".client-id", StubIdp.CLIENT_ID);
        values.put(prefix + ".client-secret", "test-secret");
        values.put(prefix + ".scopes", "groups");
        return values;
    }

    private String claims(String nonce, String extra) {
        long expiry = System.currentTimeMillis() / 1000L + 600L;
        return "{\"iss\":\"" + idp.issuer() + "\","
                + "\"aud\":\"" + StubIdp.CLIENT_ID + "\","
                + "\"sub\":\"user-123\","
                + "\"nonce\":\"" + nonce + "\","
                + "\"exp\":" + expiry + ","
                + "\"iat\":" + (expiry - 60L) + ","
                + "\"email\":\"user@example.test\","
                + "\"name\":\"Test User\","
                + "\"email_verified\":true"
                + extra
                + "}";
    }

    private static Map<String, String> params(String state, String code) {
        Map<String, String> values = new HashMap<String, String>();
        values.put("state", state);
        values.put("code", code);
        return values;
    }

    private static Map<String, String> query(String url) {
        Map<String, String> values = new HashMap<String, String>();
        String[] pairs = url.substring(url.indexOf('?') + 1).split("&");
        for (int index = 0; index < pairs.length; index++) {
            int equals = pairs[index].indexOf('=');
            values.put(
                    URLDecoder.decode(pairs[index].substring(0, equals), StandardCharsets.UTF_8),
                    URLDecoder.decode(pairs[index].substring(equals + 1), StandardCharsets.UTF_8));
        }
        return values;
    }
}
