/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.identity.auth.AuthCapability;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.LocalPasswordPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LocalPasswordPluginTest {
    private static final long FIXED_EPOCH = 1700000000L;
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "s3cret!";
    private static final String EMAIL = "admin@example.org";
    private static final String REDIRECT = "https://app.example.org/callback";

    @TempDir Path temp;

    private PluginContext context;
    private LocalPasswordPlugin plugin;
    private int loginNumber;

    @BeforeEach
    void setUp() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("local.username", USERNAME);
        settings.put("local.password", PASSWORD);
        settings.put("local.email", EMAIL);
        context = new PluginContext(temp, settings);
        context.putService(
                LocalPasswordPlugin.SERVICE_CLOCK,
                Clock.fixed(Instant.ofEpochSecond(FIXED_EPOCH), ZoneOffset.UTC));
        plugin = new LocalPasswordPlugin();
        plugin.initialize(context);
        loginNumber = 0;
    }

    @Test
    void descriptorAdvertisesPasswordTotpAndAssurance() {
        assertEquals("local", plugin.descriptor().name());
        assertTrue(plugin.supports(AuthCapability.PASSWORD));
        assertTrue(plugin.supports(AuthCapability.TOTP));
        assertTrue(plugin.supports(AuthCapability.MFA_ASSURANCE));
        assertFalse(plugin.supports(AuthCapability.OIDC));
    }

    @Test
    void startLoginReturnsLocalPathAndPersistsPendingState() throws IOException {
        String path = plugin.startLogin(context, "state-one", "nonce-one", REDIRECT);
        assertEquals("/login/local", path);
        String pending =
                new String(
                        Files.readAllBytes(temp.resolve("local").resolve("pending.json")), "UTF-8");
        assertTrue(pending.contains("state-one"));
    }

    @Test
    void firstPasswordLoginIsNotMfaAndHasPendingEnrollment() {
        AuthIdentity identity = login(PASSWORD, null);

        assertEquals("local", identity.provider());
        assertEquals(USERNAME, identity.subject());
        assertEquals(EMAIL, identity.email());
        assertFalse(identity.mfaAssured());
        assertEquals("pending", identity.claims().get("mfa_enrollment"));
        assertEquals("false", identity.claims().get("admin"));

        String sessionId = identity.claims().get("session_id");
        assertNotNull(sessionId);
        assertNotNull(plugin.currentIdentity(context, sessionId));
        assertFalse(plugin.currentIdentity(context, sessionId).mfaAssured());

        plugin.endSession(context, sessionId);
        assertNull(plugin.currentIdentity(context, sessionId));
    }

    @Test
    void wrongPasswordIsRejected() {
        expectRejected("wrong-password", null);
    }

    @Test
    void unknownUserIsRejected() {
        String state = nextState();
        plugin.startLogin(context, state, "nonce", REDIRECT);
        Map<String, String> params = params("someone", PASSWORD, null);
        params.put("state", state);
        try {
            plugin.handleCallback(context, params);
            fail("unknown user must be rejected");
        } catch (LocalAuthException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    void totpEnrollmentGrantsMfaAssuranceAndAdmin() {
        assertFalse(login(PASSWORD, null).mfaAssured());

        String code = Totp.code(plugin.totpSecret(USERNAME), FIXED_EPOCH);
        AuthIdentity assured = login(PASSWORD, code);

        assertTrue(assured.mfaAssured());
        assertEquals("complete", assured.claims().get("mfa_enrollment"));
        assertEquals("true", assured.claims().get("admin"));
        assertTrue(
                plugin.currentIdentity(context, assured.claims().get("session_id")).mfaAssured());

        LocalStore store = (LocalStore) context.service(LocalPasswordPlugin.SERVICE_STORE);
        LocalStore.User user = store.getUser(USERNAME);
        assertTrue(user.enrolled());
        assertTrue(user.admin());
        assertTrue(user.totpCounter() >= 0);
    }

    @Test
    void enrolledUserWithoutTotpIsRejected() {
        String code = Totp.code(plugin.totpSecret(USERNAME), FIXED_EPOCH);
        login(PASSWORD, code);

        expectRejected(PASSWORD, null);
    }

    @Test
    void replayedTotpCodeIsRejected() {
        String code = Totp.code(plugin.totpSecret(USERNAME), FIXED_EPOCH);
        login(PASSWORD, code);

        expectRejected(PASSWORD, code);
    }

    @Test
    void invalidTotpDuringEnrollmentIsRejected() {
        expectRejected(PASSWORD, "000000");
        LocalStore store = (LocalStore) context.service(LocalPasswordPlugin.SERVICE_STORE);
        assertFalse(store.getUser(USERNAME).enrolled());
    }

    @Test
    void totpSecretIsEncryptedAtRestAndAllFilesStayUnderDataDir() throws IOException {
        String rawSecret =
                java.util.Base64.getEncoder().encodeToString(plugin.totpSecret(USERNAME));
        String stored =
                new String(
                        Files.readAllBytes(temp.resolve("local").resolve("users.json")), "UTF-8");
        assertFalse(stored.contains(rawSecret));

        login(PASSWORD, Totp.code(plugin.totpSecret(USERNAME), FIXED_EPOCH));

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
        assertTrue(relative.contains("local/users.json"));
        assertTrue(relative.contains("local/pending.json"));
        assertTrue(relative.contains("local/sessions.json"));
        assertTrue(relative.contains("keys/local.key"));
        for (int index = 0; index < relative.size(); index++) {
            String name = relative.get(index);
            assertTrue(name.startsWith("local/") || name.startsWith("keys/"), name);
        }
    }

    private AuthIdentity login(String password, String totp) {
        String state = nextState();
        plugin.startLogin(context, state, "nonce", REDIRECT);
        Map<String, String> params = params(USERNAME, password, totp);
        params.put("state", state);
        return plugin.handleCallback(context, params);
    }

    private void expectRejected(String password, String totp) {
        String state = nextState();
        plugin.startLogin(context, state, "nonce", REDIRECT);
        Map<String, String> params = params(USERNAME, password, totp);
        params.put("state", state);
        try {
            plugin.handleCallback(context, params);
            fail("login must be rejected");
        } catch (LocalAuthException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private String nextState() {
        loginNumber++;
        return "state-" + loginNumber;
    }

    private static Map<String, String> params(String username, String password, String totp) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("username", username);
        params.put("password", password);
        if (totp != null) {
            params.put("totp", totp);
        }
        return params;
    }
}
