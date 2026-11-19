/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.wiring;

import dev.jahaziel.identity.auth.AuthPluginRegistry;
import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.plugin.PluginContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ordered list of enabled provider names, read from the {@code auth.providers} setting. */
public final class AuthProviderConfig {
    private final List<String> enabledProviders;
    private final PluginContext context;

    public AuthProviderConfig(List<String> enabledProviders, PluginContext context) {
        Objects.requireNonNull(enabledProviders, "enabledProviders");
        this.enabledProviders = new ArrayList<String>(enabledProviders);
        this.context = context;
    }

    /** Reads the comma-separated provider order from the plugin context settings. */
    public static AuthProviderConfig fromSettings(PluginContext context) {
        String raw = null;
        if (context != null) {
            raw = context.setting("auth.providers");
            if (raw == null || raw.length() == 0) {
                raw = context.setting("AUTH_PROVIDERS");
            }
        }
        List<String> names = new ArrayList<String>();
        if (raw != null) {
            String[] parts = raw.split(",");
            for (int index = 0; index < parts.length; index++) {
                String name = parts[index].trim();
                if (name.length() > 0 && !names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return new AuthProviderConfig(names, context);
    }

    /**
     * Reads only the configured order. With no explicit {@code auth.providers} setting this stays
     * empty so the existing DevConnect flow remains the fallback; a registered-but-unconfigured
     * provider must never silently become the active login path.
     */
    public static AuthProviderConfig fromSettings(
            PluginContext context, AuthPluginRegistry registry) {
        return fromSettings(context);
    }

    /** Returns the enabled providers in preference order. */
    public List<String> enabledProviders() {
        return new ArrayList<String>(enabledProviders);
    }

    /** Returns the plugin context shared with the providers. */
    public PluginContext context() {
        return context;
    }

    /** True when no provider is enabled, so the DevConnect flow should be used. */
    public boolean isEmpty() {
        return enabledProviders.isEmpty();
    }
}
