/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.AuthCapability;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.GoogleAuthPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.Test;

/** Google provider descriptor, config prefix and identity namespace. */
final class GoogleAuthPluginTest extends ProviderTestBase {
    @Test
    void descriptorAdvertisesOidc() {
        GoogleAuthPlugin plugin = new GoogleAuthPlugin();
        assertEquals("google", plugin.descriptor().name());
        assertTrue(plugin.supports(AuthCapability.OIDC));
    }

    @Test
    void identityUsesGoogleNamespace() {
        GoogleAuthPlugin plugin = new GoogleAuthPlugin();
        PluginContext context = context("google");
        plugin.initialize(context);
        AuthIdentity identity = completeLogin(plugin, context, "state-google", "nonce-google");
        assertEquals("google", identity.provider());
        assertEquals("google:user-123", identity.linkKey());
        assertTrue(identity.mfaAssured());
    }
}
