/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.AuthCapability;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.GenericOidcPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.HashMap;
import java.util.Map;

/** Generic provider descriptor, required issuer and identity namespace. */
final class GenericOidcPluginTest extends ProviderTestBase {
    @Test
    void descriptorAdvertisesOidc() {
        GenericOidcPlugin plugin = new GenericOidcPlugin();
        assertEquals("oidc", plugin.descriptor().name());
        assertTrue(plugin.supports(AuthCapability.OIDC));
    }

    @Test
    void issuerIsRequired() {
        final GenericOidcPlugin plugin = new GenericOidcPlugin();
        final PluginContext context = new PluginContext(tempDir, new HashMap<String, String>());
        OidcException failure =
                assertThrows(
                        OidcException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                plugin.initialize(context);
                            }
                        });
        assertTrue(failure.getMessage().contains("missing oidc issuer"), failure.getMessage());
    }

    @Test
    void identityUsesOidcNamespace() {
        GenericOidcPlugin plugin = new GenericOidcPlugin();
        PluginContext context = context("oidc");
        plugin.initialize(context);
        AuthIdentity identity = completeLogin(plugin, context, "state-generic", "nonce-generic");
        assertEquals("oidc", identity.provider());
        assertEquals("oidc:user-123", identity.linkKey());
        Map<String, String> claims = identity.claims();
        assertTrue(claims.containsKey("session_id"));
        assertTrue(claims.containsKey("iss"));
    }
}
