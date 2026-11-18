/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.token;

import dev.jahaziel.plugin.PluginContext;

import java.nio.file.Path;

/** Settings for the API token provider, read once at initialization. */
public final class ApiTokenConfig {
    private static final long DEFAULT_TTL_MILLIS = 60L * 60L * 1000L;

    private final long ttlMillis;
    private final String issuer;
    private final Path storeFile;

    public ApiTokenConfig(PluginContext context) {
        String configured = first(context, "api-token.ttl-millis", "API_TOKEN_TTL_MILLIS");
        long parsed = DEFAULT_TTL_MILLIS;
        if (configured != null) {
            try {
                parsed = Long.parseLong(configured);
            } catch (NumberFormatException failure) {
                throw new ApiTokenException("api-token TTL must be a number of milliseconds");
            }
            if (parsed <= 0L) {
                throw new ApiTokenException("api-token TTL must be positive");
            }
        }
        this.ttlMillis = parsed;
        String name = first(context, "api-token.issuer", "API_TOKEN_ISSUER");
        this.issuer = name == null ? "api-token" : name;
        this.storeFile = context.dataDir().resolve("api-token").resolve("api-token-store.json");
    }

    public long ttlMillis() {
        return ttlMillis;
    }

    public String issuer() {
        return issuer;
    }

    public Path storeFile() {
        return storeFile;
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
}
