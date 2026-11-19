/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.identity.auth.AuthCapability;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BasisAuthPluginTest {
    private static final String REDIRECT = "https://app.example.org/oauth/callback";
    private static final String RESOURCE = "urn:example:api";
    private static final String CLIENT_ID = "example-web";
    private static final String SECRET = "client-secret-1234567890";

    @TempDir Path temp;

    private LoopbackIdp idp;
    private Map<String, String> settings;
    private PluginContext context;
    private BasisAuthPlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        idp = LoopbackIdp.start(CLIENT_ID, SECRET, RESOURCE);
        settings = new HashMap<String, String>();
        settings.put("basis.auth-issuer", idp.issuer());
        settings.put("basis.client-id", CLIENT_ID);
        settings.put("basis.client-secret", SECRET);
        settings.put("basis.resource", RESOURCE);
        settings.put("basis.resource-secret", "resource-secret-1234567890");
        settings.put("basis.internal-url", idp.issuer());
        settings.put("basis.scopes", "projects.read");
        settings.put("basis.permissions", "projects.member");
        settings.put("basis.offline-access", "true");
        context = new PluginContext(temp, settings);
        plugin = new BasisAuthPlugin();
        plugin.initialize(context);
    }

    @AfterEach
    void tearDown() {
        idp.close();
    }

    @Test
    void descriptorAdvertisesEveryCapability() {
        assertEquals("basis-auth", plugin.descriptor().name());
        assertTrue(plugin.supports(AuthCapability.OIDC));
        assertTrue(plugin.supports(AuthCapability.OAUTH2));
        assertTrue(plugin.supports(AuthCapability.MFA_ASSURANCE));
        assertTrue(plugin.supports(AuthCapability.REFRESH));
        assertTrue(plugin.supports(AuthCapability.BACKCHANNEL));
        assertFalse(plugin.supports(AuthCapability.PASSWORD));
    }

    @Test
    void startLoginBuildsAuthorizeUrlWithoutRequestingPermissionsAsScope() {
        String url = plugin.startLogin(context, "state-1", "nonce-1", REDIRECT);

        assertTrue(url.startsWith(idp.issuer() + "/authorize?"));
        assertEquals("code", value(url, "response_type"));
        assertEquals(CLIENT_ID, value(url, "client_id"));
        assertEquals(REDIRECT, value(url, "redirect_uri"));
        assertEquals("state-1", value(url, "state"));
        assertEquals("nonce-1", value(url, "nonce"));
        assertEquals("S256", value(url, "code_challenge_method"));
        assertNotNull(value(url, "code_challenge"));
        assertEquals(RESOURCE, value(url, "resource"));
        String scope = value(url, "scope");
        assertTrue(scope.contains("openid"));
        assertTrue(scope.contains("profile"));
        assertTrue(scope.contains("email"));
        assertTrue(scope.contains("projects.read"));
        assertTrue(scope.contains("offline_access"));
        assertFalse(scope.contains("projects.member"));
        assertFalse(scope.contains("permission"));
    }

    @Test
    void offlineAccessIsOmittedUnlessConfigured() {
        Map<String, String> noOffline = new HashMap<String, String>(settings);
        noOffline.put("basis.offline-access", "false");
        PluginContext other = new PluginContext(temp.resolve("plain"), noOffline);
        BasisAuthPlugin otherPlugin = new BasisAuthPlugin();
        otherPlugin.initialize(other);

        String scope = value(otherPlugin.startLogin(other, "s", "n", REDIRECT), "scope");
        assertTrue(scope.contains("openid"));
        assertFalse(scope.contains("offline_access"));
    }

    @Test
    void callbackExchangesCodeVerifiesTokenAndManagesSession() throws Exception {
        AuthIdentity identity = login("state-2", "nonce-2");

        assertEquals("basis-auth", identity.provider());
        assertEquals("basis-sub-1", identity.subject());
        assertEquals("user@example.org", identity.email());
        assertEquals("Basis User", identity.displayName());
        assertTrue(identity.emailVerified());
        assertTrue(identity.mfaAssured());
        assertEquals("pwd mfa", identity.claims().get("amr"));
        String sessionId = identity.claims().get("session_id");
        assertNotNull(sessionId);
        assertEquals("basis-sub-1", plugin.currentIdentity(context, sessionId).subject());
        assertEquals(1, idp.tokenHits());

        plugin.endSession(context, sessionId);
        assertNull(plugin.currentIdentity(context, sessionId));
    }

    @Test
    void stateMismatchRejectsCallback() {
        plugin.startLogin(context, "right-state", "nonce-3", REDIRECT);
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", "wrong-state");
        parameters.put("code", "anything");
        try {
            plugin.handleCallback(context, parameters);
            fail("state mismatch must be rejected");
        } catch (BasisException expected) {
            assertTrue(expected.getMessage().contains("state"));
        }
        assertEquals(0, idp.tokenHits());
    }

    @Test
    void accessDeniedIsCancellationWithoutExchange() {
        plugin.startLogin(context, "state-4", "nonce-4", REDIRECT);
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", "state-4");
        parameters.put("error", "access_denied");
        try {
            plugin.handleCallback(context, parameters);
            fail("access_denied must cancel the login");
        } catch (BasisLoginCancelledException expected) {
            assertNotNull(expected.getMessage());
        }
        assertEquals(0, idp.tokenHits());
    }

    @Test
    void jwksIsFetchedOnceForMultipleCallbacks() throws Exception {
        login("state-5", "nonce-5");
        login("state-6", "nonce-6");
        assertEquals(1, idp.jwksHits());
    }

    @Test
    void mfaFromAcrIsRecognizedAndAbsentMfaIsNot() throws Exception {
        idp.assurance(Arrays.asList("pwd"), "urn:example:assurance:mfa");
        assertTrue(login("state-7", "nonce-7").mfaAssured());

        idp.assurance(Arrays.asList("pwd"), "1");
        assertFalse(login("state-8", "nonce-8").mfaAssured());
    }

    @Test
    void refreshTokenIsEncryptedAndPersistedUnderDataDir() throws Exception {
        AuthIdentity identity = login("state-9", "nonce-9");
        String sessionId = identity.claims().get("session_id");
        Path storeFile = temp.resolve("identity").resolve("basis-auth-store.json");
        Path keyFile = temp.resolve("keys").resolve("basis-auth.key");
        assertTrue(Files.exists(storeFile));
        assertTrue(Files.exists(keyFile));
        String stored = new String(Files.readAllBytes(storeFile), "UTF-8");
        assertFalse(stored.contains(idp.refreshToken()));

        BasisStore store = (BasisStore) context.service(BasisAuthPlugin.SERVICE_STORE);
        assertEquals(idp.refreshToken(), store.refreshToken(sessionId));
    }

    @Test
    void everyPersistedFileStaysUnderTheDataDir() throws Exception {
        login("state-10", "nonce-10");
        final List<String> relative = new ArrayList<String>();
        Files.walkFileTree(
                temp,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        relative.add(temp.relativize(file).toString().replace('\\', '/'));
                        return FileVisitResult.CONTINUE;
                    }
                });
        assertTrue(relative.contains("identity/basis-auth-store.json"));
        assertTrue(relative.contains("keys/basis-auth.key"));
        for (int index = 0; index < relative.size(); index++) {
            String name = relative.get(index);
            assertTrue(name.startsWith("identity/") || name.startsWith("keys/"), name);
        }
    }

    private AuthIdentity login(String state, String nonce) throws Exception {
        String url = plugin.startLogin(context, state, nonce, REDIRECT);
        String code = idp.authorize(url);
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", state);
        parameters.put("code", code);
        return plugin.handleCallback(context, parameters);
    }

    private static String value(String url, String name) {
        String query = url.contains("?") ? url.substring(url.indexOf('?') + 1) : "";
        String[] pairs = query.split("&");
        for (int index = 0; index < pairs.length; index++) {
            int equal = pairs[index].indexOf('=');
            if (equal > 0 && pairs[index].substring(0, equal).equals(name)) {
                return java.net.URLDecoder.decode(
                        pairs[index].substring(equal + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
