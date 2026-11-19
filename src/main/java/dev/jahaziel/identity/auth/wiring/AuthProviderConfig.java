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
     * Reads the configured order, or derives it from the already-gated registry when no explicit
     * order is set. An empty registry yields an empty config, which selects the DevConnect
     * fallback.
     */
    public static AuthProviderConfig fromSettings(
            PluginContext context, AuthPluginRegistry registry) {
        AuthProviderConfig explicit = fromSettings(context);
        if (!explicit.isEmpty() || registry == null) {
            return explicit;
        }
        List<AuthProviderPlugin> available = registry.all();
        List<String> names = new ArrayList<String>();
        for (int index = 0; index < available.size(); index++) {
            names.add(available.get(index).descriptor().name());
        }
        return new AuthProviderConfig(names, context);
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
