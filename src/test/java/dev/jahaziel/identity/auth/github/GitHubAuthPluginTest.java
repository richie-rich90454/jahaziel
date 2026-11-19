/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.AuthCapability;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.GitHubAuthPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLDecoder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GitHubAuthPluginTest {
    private static final String CLIENT_ID = "octo-client";
    private static final String SECRET = "octo-secret-1234567890";
    private static final String REDIRECT = "https://app.example.org/oauth/github";
    private static final String ACCESS_TOKEN = "test-access-token";

    @TempDir Path temp;

    private LoopbackGitHub stub;
    private Map<String, String> settings;
    private PluginContext context;
    private GitHubAuthPlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        stub = LoopbackGitHub.start();
        settings = new HashMap<String, String>();
        settings.put("github.client-id", CLIENT_ID);
        settings.put("github.client-secret", SECRET);
        settings.put("github.authorize-url", stub.base() + "/login/oauth/authorize");
        settings.put("github.token-url", stub.base() + "/login/oauth/access_token");
        settings.put("github.user-url", stub.base() + "/user");
        settings.put("github.emails-url", stub.base() + "/user/emails");
        context = new PluginContext(temp, settings);
        plugin = new GitHubAuthPlugin();
        plugin.initialize(context);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    void descriptorAdvertisesOAuth2AndMfaButNotOidc() {
        assertEquals("github", plugin.descriptor().name());
        assertTrue(plugin.supports(AuthCapability.OAUTH2));
        assertTrue(plugin.supports(AuthCapability.MFA_ASSURANCE));
        assertFalse(plugin.supports(AuthCapability.OIDC));
    }

    @Test
    void startLoginBuildsAuthorizeUrlWithRequiredParameters() {
        String url = plugin.startLogin(context, "state-1", "nonce-ignored", REDIRECT);

        assertTrue(url.startsWith(stub.base() + "/login/oauth/authorize?"));
        assertEquals(CLIENT_ID, value(url, "client_id"));
        assertEquals(REDIRECT, value(url, "redirect_uri"));
        assertEquals("state-1", value(url, "state"));
        assertEquals("code", value(url, "response_type"));
        assertEquals("read:user user:email", value(url, "scope"));
        assertNull(value(url, "id_token"));
    }

    @Test
    void scopeIsConfigurable() {
        Map<String, String> custom = new HashMap<String, String>(settings);
        custom.put("github.scopes", "read:user");
        PluginContext other = new PluginContext(temp.resolve("custom"), custom);
        GitHubAuthPlugin otherPlugin = new GitHubAuthPlugin();
        otherPlugin.initialize(other);

        String url = otherPlugin.startLogin(other, "s", "n", REDIRECT);
        assertEquals("read:user", value(url, "scope"));
    }

    @Test
    void stateMismatchRejectsBeforeAnyExchange() {
        plugin.startLogin(context, "right-state", "n", REDIRECT);
        Map<String, String> callback = new HashMap<String, String>();
        callback.put("state", "wrong-state");
        callback.put("code", "good-code");

        GitHubException failure = assertThrows(GitHubException.class, new CallbackRun(callback));
        assertTrue(failure.getMessage().contains("state"));
        assertEquals(0, stub.tokenHits());
    }

    @Test
    void accessDeniedIsCancellationWithoutExchange() {
        plugin.startLogin(context, "state-2", "n", REDIRECT);
        Map<String, String> callback = new HashMap<String, String>();
        callback.put("state", "state-2");
        callback.put("error", "access_denied");

        GitHubLoginCancelledException cancelled =
                assertThrows(GitHubLoginCancelledException.class, new CallbackRun(callback));
        assertNotNull(cancelled.getMessage());
        assertEquals(0, stub.tokenHits());
    }

    @Test
    void subjectComesFromNumericIdNeverLogin() {
        stub.userId(999888777L);
        stub.login("renamed-login");

        AuthIdentity identity = login("state-3");

        assertEquals("github", identity.provider());
        assertEquals("999888777", identity.subject());
        assertFalse("renamed-login".equals(identity.subject()));
        assertEquals("The Octocat", identity.displayName());
    }

    @Test
    void primaryVerifiedEmailIsSelected() {
        AuthIdentity identity = login("state-4");

        assertEquals("primary@example.org", identity.email());
        assertTrue(identity.emailVerified());
    }

    @Test
    void verifiedNonPrimaryWinsWhenPrimaryIsUnverified() {
        stub.emails(
                "[{\"email\":\"primary-unverified@example.org\",\"primary\":true,\"verified\":false},"
                        + "{\"email\":\"verified@example.org\",\"primary\":false,\"verified\":true}]");

        AuthIdentity identity = login("state-5");

        assertEquals("verified@example.org", identity.email());
        assertTrue(identity.emailVerified());
    }

    @Test
    void mfaIsFalseUnlessEvidenceIsObserved() {
        assertFalse(login("state-6").mfaAssured());

        stub.otp("required; app");
        assertTrue(login("state-7").mfaAssured());

        stub.otp(null);
        stub.twoFactor(true);
        assertTrue(login("state-8").mfaAssured());
    }

    @Test
    void tokenAndUserCallsRequestJsonAndUseBearer() {
        AuthIdentity identity = login("state-9");

        assertTrue(stub.tokenAccept().contains("application/json"));
        assertTrue(stub.userAccept().contains("application/json"));
        assertEquals(CLIENT_ID, stub.tokenFormValue("client_id"));
        assertEquals(SECRET, stub.tokenFormValue("client_secret"));
        assertEquals("authorization_code", stub.tokenFormValue("grant_type"));
        assertEquals(REDIRECT, stub.tokenFormValue("redirect_uri"));
        assertNotNull(identity.claims().get("session_id"));
        assertEquals(1, stub.tokenHits());
        assertEquals(1, stub.userHits());
        assertEquals(1, stub.emailHits());
    }

    @Test
    void sessionLifecycleAndEncryptedTokenStayUnderDataDir() throws Exception {
        AuthIdentity identity = login("state-10");
        String sessionId = identity.claims().get("session_id");

        assertEquals(identity.subject(), plugin.currentIdentity(context, sessionId).subject());
        GitHubStore store = (GitHubStore) context.service(GitHubAuthPlugin.SERVICE_STORE);
        assertEquals(ACCESS_TOKEN, store.accessToken(sessionId));

        Path storeFile = temp.resolve("identity").resolve("github-auth-store.json");
        Path keyFile = temp.resolve("keys").resolve("github-auth.key");
        assertTrue(Files.exists(storeFile));
        assertTrue(Files.exists(keyFile));
        String stored = new String(Files.readAllBytes(storeFile), "UTF-8");
        assertFalse(stored.contains(ACCESS_TOKEN));

        plugin.endSession(context, sessionId);
        assertNull(plugin.currentIdentity(context, sessionId));
    }

    @Test
    void everyPersistedFileStaysUnderTheDataDir() throws Exception {
        login("state-11");
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
        assertTrue(relative.contains("identity/github-auth-store.json"));
        assertTrue(relative.contains("keys/github-auth.key"));
        for (int index = 0; index < relative.size(); index++) {
            String name = relative.get(index);
            assertTrue(name.startsWith("identity/") || name.startsWith("keys/"), name);
        }
    }

    private AuthIdentity login(String state) {
        plugin.startLogin(context, state, "n", REDIRECT);
        Map<String, String> callback = new HashMap<String, String>();
        callback.put("state", state);
        callback.put("code", "good-code");
        return plugin.handleCallback(context, callback);
    }

    private static String value(String url, String name) {
        String query = url.contains("?") ? url.substring(url.indexOf('?') + 1) : "";
        String[] pairs = query.split("&");
        for (int index = 0; index < pairs.length; index++) {
            int equal = pairs[index].indexOf('=');
            if (equal > 0 && pairs[index].substring(0, equal).equals(name)) {
                return URLDecoder.decode(
                        pairs[index].substring(equal + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private final class CallbackRun implements org.junit.jupiter.api.function.Executable {
        private final Map<String, String> callback;

        private CallbackRun(Map<String, String> callback) {
            this.callback = callback;
        }

        @Override
        public void execute() {
            plugin.handleCallback(context, callback);
        }
    }
}
