/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import dev.jahaziel.plugin.PluginContext;

import java.nio.file.Path;

/** Settings for the basis-auth provider, read once at initialization. */
public final class BasisConfig {
    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final String resource;
    private final String resourceSecret;
    private final String internalUrl;
    private final String scopes;
    private final boolean offlineAccess;
    private final Path identityDir;
    private final Path keysDir;

    public BasisConfig(PluginContext context) {
        this.issuer = required(context, "missing basis-auth issuer",
                "basis.auth-issuer", "BASIS_AUTH_ISSUER");
        this.clientId = required(context, "missing basis-auth client id",
                "basis.client-id", "BASIS_CLIENT_ID");
        this.clientSecret = first(context,
                "basis.client-secret", "BASIS_CLIENT_SECRET");
        this.resource = required(context, "missing basis-auth resource",
                "basis.resource", "BASIS_RESOURCE");
        this.resourceSecret = first(context,
                "basis.resource-secret", "BASIS_RESOURCE_SECRET");
        this.internalUrl = first(context,
                "basis.internal-url", "BASIS_AUTH_INTERNAL_URL", "BASIS_INTERNAL_URL");
        this.scopes = first(context, "basis.scopes", "BASIS_AUTH_SCOPES", "BASIS_SCOPES");
        this.offlineAccess = Boolean.parseBoolean(first(context,
                "basis.offline-access", "BASIS_AUTH_OFFLINE_ACCESS"));
        this.identityDir = context.dataDir().resolve("identity");
        this.keysDir = context.dataDir().resolve("keys");
    }

    public String issuer() {
        return issuer;
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public boolean publicClient() {
        return clientSecret == null || clientSecret.length() == 0;
    }

    public String resource() {
        return resource;
    }

    public String resourceSecret() {
        return resourceSecret;
    }

    public String internalUrl() {
        return internalUrl;
    }

    public String scopes() {
        return scopes == null ? "" : scopes.trim();
    }

    public boolean offlineAccess() {
        return offlineAccess;
    }

    public Path identityDir() {
        return identityDir;
    }

    public Path keysDir() {
        return keysDir;
    }

    private static String first(PluginContext context, String... keys) {
        for (int index = 0; index < keys.length; index++) {
            String value = context.setting(keys[index]);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return null;
    }

    private static String required(PluginContext context, String message, String... keys) {
        String value = first(context, keys);
        if (value == null) {
            throw new BasisException(message);
        }
        return value;
    }
}
