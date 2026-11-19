/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.identity.auth.oidc.OidcProviderPlugin;
import dev.jahaziel.plugin.PluginDescriptor;

import java.util.Arrays;
import java.util.Collections;

/** Generic OIDC provider for any conforming issuer. Config keys use the "oidc" prefix. */
public final class GenericOidcPlugin extends OidcProviderPlugin {
    private static final PluginDescriptor DESCRIPTOR =
            new PluginDescriptor(
                    "oidc",
                    "1",
                    Collections.<String>emptyList(),
                    Arrays.asList(
                            AuthCapability.OIDC,
                            AuthCapability.OAUTH2,
                            AuthCapability.MFA_ASSURANCE));

    public GenericOidcPlugin() {
        super(DESCRIPTOR);
    }

    @Override
    protected String providerName() {
        return "oidc";
    }

    @Override
    protected String configPrefix() {
        return "oidc";
    }
}
