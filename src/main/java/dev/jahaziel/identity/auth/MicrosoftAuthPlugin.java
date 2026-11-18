/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.identity.auth.oidc.OidcProviderPlugin;
import dev.jahaziel.plugin.PluginDescriptor;

import java.util.Arrays;
import java.util.Collections;

/** Microsoft Entra ID (v2) OIDC provider. Config keys use the "microsoft" prefix. */
public final class MicrosoftAuthPlugin extends OidcProviderPlugin {
    private static final PluginDescriptor DESCRIPTOR = new PluginDescriptor(
            "microsoft",
            "1",
            Collections.<String>emptyList(),
            Arrays.asList(AuthCapability.OIDC, AuthCapability.OAUTH2, AuthCapability.MFA_ASSURANCE));

    public MicrosoftAuthPlugin() {
        super(DESCRIPTOR);
    }

    @Override
    protected String providerName() {
        return "microsoft";
    }

    @Override
    protected String configPrefix() {
        return "microsoft";
    }

    @Override
    protected String defaultIssuer() {
        return "https://login.microsoftonline.com/common/v2.0";
    }

    @Override
    protected boolean issuerMatches(String expected, String actual) {
        if (super.issuerMatches(expected, actual)) {
            return true;
        }
        return expected.replace("/common/", "/{tenantid}/").equals(actual);
    }
}
