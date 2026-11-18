/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuthPluginRegistryTest {
    @Test
    void registersAndOrdersProviders() {
        AuthPluginRegistry registry = new AuthPluginRegistry();
        registry.register(provider("github"));
        registry.register(provider("basis-auth"));
        PluginContext context =
                new PluginContext(Paths.get("data"), Collections.<String, String>emptyMap());
        registry.initializeAll(context);

        AuthProviderPlugin basis = registry.find("basis-auth");
        assertNotNull(basis);
        assertTrue(basis.supports(AuthCapability.OIDC));
        assertEquals("basis-auth", basis.currentIdentity(context, "session").provider());
        assertNull(registry.find("missing"));

        List<AuthProviderPlugin> ordered =
                registry.byPriority(Arrays.asList("basis-auth", "github", "missing"));
        assertEquals(2, ordered.size());
        assertEquals("basis-auth", ordered.get(0).descriptor().name());
        assertEquals("github", ordered.get(1).descriptor().name());
    }

    private static AuthProviderPlugin provider(String name) {
        return new AuthProviderPlugin(
                new PluginDescriptor(
                        name,
                        "1",
                        Collections.<String>emptyList(),
                        Collections.singletonList(AuthCapability.OIDC))) {
            @Override
            public String startLogin(
                    PluginContext context, String state, String nonce, String redirectUri) {
                return "https://example.invalid/authorize";
            }

            @Override
            public AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters) {
                return identity(name);
            }

            @Override
            public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
                return identity(name);
            }

            @Override
            public void endSession(PluginContext context, String sessionId) {
            }

            @Override
            public void initialize(PluginContext context) {
            }

            @Override
            public void shutdown() {
            }
        };
    }

    private static AuthIdentity identity(String provider) {
        return new AuthIdentity(
                provider,
                "subject-1",
                "user@example.invalid",
                "Example User",
                true,
                true,
                new HashMap<String, String>());
    }
}
