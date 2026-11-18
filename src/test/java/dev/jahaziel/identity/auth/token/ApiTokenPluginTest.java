/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jahaziel.identity.auth.ApiTokenPlugin;
import dev.jahaziel.identity.auth.AuthCapability;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ApiTokenPluginTest {
    @TempDir Path temp;

    private PluginContext context;
    private ApiTokenPlugin plugin;

    @BeforeEach
    void setUp() {
        Map<String, String> settings = new HashMap<String, String>();
        context = new PluginContext(temp, settings);
        plugin = new ApiTokenPlugin();
        plugin.initialize(context);
    }

    @Test
    void descriptorAdvertisesOnlyApiToken() {
        assertEquals("api-token", plugin.descriptor().name());
        assertTrue(plugin.supports(AuthCapability.API_TOKEN));
        assertFalse(plugin.supports(AuthCapability.PASSWORD));
        assertFalse(plugin.supports(AuthCapability.PASSKEY));
    }

    @Test
    void hashingAndScopeChecks() throws IOException {
        String token =
                plugin.tokens().issue("user-1", "user@example.org", "User", "read write,admin");

        TokenRecord verified = plugin.tokens().verify(token);
        assertNotNull(verified);
        assertEquals("user-1", verified.subject());
        assertEquals("user@example.org", verified.email());
        assertTrue(verified.hasScope("read"));
        assertTrue(verified.hasScope("admin"));
        assertFalse(verified.hasScope("delete"));
        assertTrue(plugin.tokens().hasScope(token, "write"));
        assertFalse(plugin.tokens().hasScope(token, "delete"));

        Path store = temp.resolve("api-token").resolve("api-token-store.json");
        String stored = new String(Files.readAllBytes(store), StandardCharsets.UTF_8);
        assertFalse(stored.contains(token));
        assertTrue(stored.contains(verified.hash()));
        assertTrue(Files.exists(store));
    }

    @Test
    void expiryIsEnforcedAndAudited() {
        String token = plugin.tokens().issue("user-2", "", "", "read");
        TokenRecord record = plugin.tokens().verify(token);
        assertNotNull(record);

        assertNull(plugin.tokens().verifyAt(token, record.expiresAt() + 1L));
        assertNull(plugin.tokens().verify(token));
        assertTrue(plugin.audit().events().contains("expire:" + record.id()));
    }

    @Test
    void rotationInvalidatesTheOldToken() {
        String token = plugin.tokens().issue("user-3", "", "", "read");
        TokenRecord original = plugin.tokens().verify(token);
        assertNotNull(original);

        String rotated = plugin.tokens().rotate(token);
        assertFalse(rotated.equals(token));
        assertNull(plugin.tokens().verify(token));
        TokenRecord after = plugin.tokens().verify(rotated);
        assertNotNull(after);
        assertEquals("user-3", after.subject());
        assertEquals(original.expiresAt(), after.expiresAt());
        assertTrue(plugin.audit().events().contains("rotate:" + original.id()));
    }

    @Test
    void revocationInvalidatesTheToken() {
        String token = plugin.tokens().issue("user-4", "", "", "read");
        assertTrue(plugin.tokens().revoke(token));
        assertNull(plugin.tokens().verify(token));
        assertFalse(plugin.tokens().revoke(token));
    }

    @Test
    void callbackRequiresTokenAndNeverCreatesASession() {
        Map<String, String> empty = new HashMap<String, String>();
        try {
            plugin.handleCallback(context, empty);
            fail("missing token must be rejected");
        } catch (ApiTokenException expected) {
            assertTrue(expected.getMessage().contains("token"));
        }

        String token = plugin.tokens().issue("user-5", "u5@example.org", "User Five", "read");
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("token", token);
        AuthIdentity identity = plugin.handleCallback(context, parameters);
        assertEquals("api-token", identity.provider());
        assertEquals("user-5", identity.subject());
        assertEquals("read", identity.claims().get("scope"));
        assertFalse(identity.mfaAssured());
        assertFalse(identity.emailVerified());

        assertNull(plugin.currentIdentity(context, identity.claims().get("token_id")));
        assertTrue(plugin.audit().events().contains("deny:browser-session-rejected"));
    }

    @Test
    void auditListenerHookReceivesEvents() {
        final List<String> seen = new ArrayList<String>();
        PluginContext hooked =
                new PluginContext(temp.resolve("hook"), new HashMap<String, String>());
        hooked.putService(
                ApiTokenPlugin.SERVICE_AUDIT_LISTENER,
                new AuditListener() {
                    @Override
                    public void onEvent(String event, String detail) {
                        seen.add(event + ":" + detail);
                    }
                });
        ApiTokenPlugin other = new ApiTokenPlugin();
        other.initialize(hooked);
        other.tokens().issue("user-6", "", "", "read");

        assertFalse(seen.isEmpty());
        assertTrue(seen.get(0).startsWith("issue:"));
        assertEquals(1, seen.size());
    }

    @Test
    void everyPersistedFileStaysUnderTheDataDir() throws IOException {
        plugin.tokens().issue("user-7", "", "", "read");
        final List<String> relative = new ArrayList<String>();
        Files.walkFileTree(
                temp,
                new java.nio.file.SimpleFileVisitor<Path>() {
                    @Override
                    public java.nio.file.FileVisitResult visitFile(
                            Path file, java.nio.file.attribute.BasicFileAttributes attributes) {
                        relative.add(temp.relativize(file).toString().replace('\\', '/'));
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
        for (int index = 0; index < relative.size(); index++) {
            String name = relative.get(index);
            assertTrue(name.startsWith("api-token") || name.isEmpty(), name);
        }
    }
}
