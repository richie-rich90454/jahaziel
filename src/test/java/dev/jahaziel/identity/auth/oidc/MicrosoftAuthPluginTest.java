/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.AuthCapability;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.MicrosoftAuthPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.Test;

/** Microsoft provider descriptor, config prefix and identity namespace. */
final class MicrosoftAuthPluginTest extends ProviderTestBase {
    @Test
    void descriptorAdvertisesOidc() {
        MicrosoftAuthPlugin plugin = new MicrosoftAuthPlugin();
        assertEquals("microsoft", plugin.descriptor().name());
        assertTrue(plugin.supports(AuthCapability.OIDC));
    }

    @Test
    void identityUsesMicrosoftNamespace() {
        MicrosoftAuthPlugin plugin = new MicrosoftAuthPlugin();
        PluginContext context = context("microsoft");
        plugin.initialize(context);
        AuthIdentity identity = completeLogin(plugin, context, "state-ms", "nonce-ms");
        assertEquals("microsoft", identity.provider());
        assertEquals("microsoft:user-123", identity.linkKey());
        assertTrue(identity.mfaAssured());
    }
}
