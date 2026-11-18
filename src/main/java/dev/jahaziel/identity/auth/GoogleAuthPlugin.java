/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.identity.auth.oidc.OidcProviderPlugin;
import dev.jahaziel.plugin.PluginDescriptor;

import java.util.Arrays;
import java.util.Collections;

/** Google OIDC provider. Config keys use the "google" prefix. */
public final class GoogleAuthPlugin extends OidcProviderPlugin {
    private static final PluginDescriptor DESCRIPTOR = new PluginDescriptor(
            "google",
            "1",
            Collections.<String>emptyList(),
            Arrays.asList(AuthCapability.OIDC, AuthCapability.OAUTH2, AuthCapability.MFA_ASSURANCE));

    public GoogleAuthPlugin() {
        super(DESCRIPTOR);
    }

    @Override
    protected String providerName() {
        return "google";
    }

    @Override
    protected String configPrefix() {
        return "google";
    }

    @Override
    protected String defaultIssuer() {
        return "https://accounts.google.com";
    }
}
