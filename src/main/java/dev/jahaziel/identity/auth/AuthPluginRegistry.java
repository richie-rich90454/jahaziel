/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.plugin.Plugin;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ordered registry of enabled authentication providers. */
public final class AuthPluginRegistry {
    private final PluginRegistry registry;

    public AuthPluginRegistry() {
        this(new PluginRegistry());
    }

    public AuthPluginRegistry(PluginRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void register(AuthProviderPlugin plugin) {
        registry.register(plugin);
    }

    public AuthProviderPlugin find(String name) {
        Plugin candidate = registry.find(name);
        if (candidate instanceof AuthProviderPlugin) {
            return (AuthProviderPlugin) candidate;
        }
        return null;
    }

    public List<AuthProviderPlugin> all() {
        List<AuthProviderPlugin> providers = new ArrayList<AuthProviderPlugin>();
        for (Plugin candidate : registry.all()) {
            if (candidate instanceof AuthProviderPlugin) {
                providers.add((AuthProviderPlugin) candidate);
            }
        }
        return providers;
    }

    public List<AuthProviderPlugin> byPriority(List<String> names) {
        List<AuthProviderPlugin> ordered = new ArrayList<AuthProviderPlugin>();
        for (String name : names) {
            AuthProviderPlugin provider = find(name);
            if (provider != null) {
                ordered.add(provider);
            }
        }
        return ordered;
    }

    public void initializeAll(PluginContext context) {
        registry.initializeAll(context);
    }

    public void shutdownAll() {
        registry.shutdownAll();
    }
}
