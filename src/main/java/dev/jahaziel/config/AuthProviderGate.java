/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides whether an auth provider has the settings it needs before initialization is attempted.
 * Providers that only use safe defaults need no settings and are always ready.
 */
public final class AuthProviderGate {
    private static final Logger log = LoggerFactory.getLogger(AuthProviderGate.class);

    /** Returns true when the provider is configured, logging and returning false when it is not. */
    public boolean ready(PluginContext context, AuthProviderPlugin provider) {
        String name = provider.descriptor().name();
        String[] required = requiredSettings(name);
        for (int index = 0; index < required.length; index++) {
            if (!present(context, required[index])) {
                log.warn(
                        "Auth provider '{}' is not configured (missing '{}'); skipping it",
                        name,
                        required[index]);
                return false;
            }
        }
        return true;
    }

    private static boolean present(PluginContext context, String key) {
        if (has(context, key)) {
            return true;
        }
        StringBuilder envKey = new StringBuilder();
        for (int index = 0; index < key.length(); index++) {
            char current = key.charAt(index);
            if (current == '.' || current == '-') {
                envKey.append('_');
            } else {
                envKey.append(Character.toUpperCase(current));
            }
        }
        return has(context, envKey.toString());
    }

    private static boolean has(PluginContext context, String key) {
        String value = context.setting(key);
        return value != null && value.trim().length() > 0;
    }

    private static String[] requiredSettings(String name) {
        if ("basis-auth".equals(name)) {
            return new String[] {"basis.auth-issuer", "basis.client-id", "basis.resource"};
        }
        if ("microsoft".equals(name)) {
            return new String[] {"microsoft.client-id"};
        }
        if ("google".equals(name)) {
            return new String[] {"google.client-id"};
        }
        if ("github".equals(name)) {
            return new String[] {"github.client-id"};
        }
        if ("oidc".equals(name)) {
            return new String[] {"oidc.client-id", "oidc.issuer"};
        }
        if ("passkey".equals(name)) {
            return new String[] {"passkey.origin", "passkey.rp-id"};
        }
        if ("ldap".equals(name)) {
            return new String[] {"ldap.url", "ldap.base-dn"};
        }
        // local and api-token have safe defaults; no setting is mandatory for them.
        return new String[0];
    }
}
