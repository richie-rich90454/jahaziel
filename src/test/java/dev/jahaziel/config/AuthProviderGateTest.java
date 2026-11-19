/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.ApiTokenPlugin;
import dev.jahaziel.identity.auth.AuthPluginRegistry;
import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.identity.auth.GenericOidcPlugin;
import dev.jahaziel.identity.auth.GitHubAuthPlugin;
import dev.jahaziel.identity.auth.GoogleAuthPlugin;
import dev.jahaziel.identity.auth.LdapPlugin;
import dev.jahaziel.identity.auth.LocalPasswordPlugin;
import dev.jahaziel.identity.auth.MicrosoftAuthPlugin;
import dev.jahaziel.identity.auth.PasskeyPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exercises the config gate without booting a Spring context or contacting an identity provider.
 */
class AuthProviderGateTest {
    @TempDir Path dataDir;

    @Test
    void emptySettingsSkipConfiguredProvidersWithoutThrowing() {
        PluginContext context = new PluginContext(dataDir, new HashMap<String, String>());
        AuthProviderGate gate = new AuthProviderGate();

        assertFalse(gate.ready(context, new BasisAuthPlugin()));
        assertFalse(gate.ready(context, new MicrosoftAuthPlugin()));
        assertFalse(gate.ready(context, new GoogleAuthPlugin()));
        assertFalse(gate.ready(context, new GitHubAuthPlugin()));
        assertFalse(gate.ready(context, new GenericOidcPlugin()));
        assertFalse(gate.ready(context, new PasskeyPlugin()));
        assertFalse(gate.ready(context, new LdapPlugin()));
    }

    @Test
    void providersWithSafeDefaultsAreAlwaysReady() {
        PluginContext context = new PluginContext(dataDir, new HashMap<String, String>());
        AuthProviderGate gate = new AuthProviderGate();

        assertTrue(gate.ready(context, new LocalPasswordPlugin()));
        assertTrue(gate.ready(context, new ApiTokenPlugin()));
    }

    @Test
    void completeSettingsEnableTheProvider() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("basis.auth-issuer", "https://idp.example.com");
        settings.put("basis.client-id", "client");
        settings.put("basis.resource", "resource");
        PluginContext context = new PluginContext(dataDir, settings);
        AuthProviderGate gate = new AuthProviderGate();

        assertTrue(gate.ready(context, new BasisAuthPlugin()));
    }

    @Test
    void envStyleSettingsEnableTheProvider() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("GOOGLE_CLIENT_ID", "client");
        PluginContext context = new PluginContext(dataDir, settings);
        AuthProviderGate gate = new AuthProviderGate();

        assertTrue(gate.ready(context, new GoogleAuthPlugin()));
    }

    @Test
    void registrySkipsUnconfiguredProvidersAndKeepsPriorityOrder() {
        PluginContext context = new PluginContext(dataDir, new HashMap<String, String>());
        List<AuthProviderPlugin> providers = new ArrayList<AuthProviderPlugin>();
        providers.add(new LdapPlugin());
        providers.add(new BasisAuthPlugin());
        providers.add(new LocalPasswordPlugin());
        providers.add(new ApiTokenPlugin());
        providers.add(new GoogleAuthPlugin());

        AuthPluginRegistry registry =
                new PluginWiringConfig()
                        .authPluginRegistry(context, new AuthProviderGate(), providers);

        assertEquals(2, registry.all().size());
        assertEquals("local", registry.all().get(0).descriptor().name());
        assertEquals("api-token", registry.all().get(1).descriptor().name());
        assertEquals(null, registry.find("basis-auth"));
    }
}
