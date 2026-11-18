/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.passkey;

import dev.jahaziel.plugin.PluginContext;

import java.nio.file.Path;

/** Settings for the passkey provider, read once at initialization. */
public final class PasskeyConfig {
    private final String origin;
    private final String rpId;
    private final String rpName;
    private final long challengeLifetimeMillis;
    private final Path storeFile;

    public PasskeyConfig(PluginContext context) {
        this.origin =
                required(context, "missing passkey origin", "passkey.origin", "PASSKEY_ORIGIN");
        this.rpId =
                required(
                        context,
                        "missing passkey relying party id",
                        "passkey.rp-id",
                        "PASSKEY_RP_ID");
        String name = first(context, "passkey.rp-name", "PASSKEY_RP_NAME");
        this.rpName = name == null ? "Jahaziel" : name;
        this.challengeLifetimeMillis = 5L * 60L * 1000L;
        this.storeFile = context.dataDir().resolve("passkey").resolve("passkey-store.json");
    }

    public String origin() {
        return origin;
    }

    public String rpId() {
        return rpId;
    }

    public String rpName() {
        return rpName;
    }

    public long challengeLifetimeMillis() {
        return challengeLifetimeMillis;
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

    private static String required(PluginContext context, String message, String... keys) {
        String value = first(context, keys);
        if (value == null) {
            throw new PasskeyException(message);
        }
        return value;
    }
}
