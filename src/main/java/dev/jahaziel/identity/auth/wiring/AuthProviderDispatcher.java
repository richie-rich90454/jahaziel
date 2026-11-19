/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.wiring;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.AuthPluginRegistry;
import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.identity.auth.basis.BackchannelHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Selects the first enabled provider from the configured order and forwards the login calls to it.
 * When no provider is available, every method reports "not handled" so the caller can use the
 * existing DevConnect flow instead of failing login startup.
 */
public final class AuthProviderDispatcher {
    private final AuthPluginRegistry registry;
    private final AuthProviderConfig config;
    private boolean resolved;
    private AuthProviderPlugin active;

    public AuthProviderDispatcher(AuthPluginRegistry registry, AuthProviderConfig config) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Returns the selected provider, or null when none is available. */
    public AuthProviderPlugin active() {
        if (resolved) {
            return active;
        }
        resolved = true;
        List<AuthProviderPlugin> candidates = registry.byPriority(config.enabledProviders());
        for (int index = 0; index < candidates.size(); index++) {
            AuthProviderPlugin candidate = candidates.get(index);
            if (candidate != null) {
                active = candidate;
                return active;
            }
        }
        return null;
    }

    /** True when a provider will handle the login. */
    public boolean handles() {
        return active() != null;
    }

    /** Delegates startLogin, or returns null when no provider handles the request. */
    public String startLogin(String state, String nonce, String redirectUri) {
        AuthProviderPlugin provider = active();
        return provider == null
                ? null
                : provider.startLogin(config.context(), state, nonce, redirectUri);
    }

    /** Delegates the callback, or returns null when no provider handles the request. */
    public AuthIdentity handleCallback(Map<String, String> parameters) {
        AuthProviderPlugin provider = active();
        return provider == null ? null : provider.handleCallback(config.context(), parameters);
    }

    /** Delegates the identity lookup, or returns null when no provider handles the request. */
    public AuthIdentity currentIdentity(String sessionId) {
        AuthProviderPlugin provider = active();
        return provider == null ? null : provider.currentIdentity(config.context(), sessionId);
    }

    /** Delegates session teardown when a provider handles the request. */
    public void endSession(String sessionId) {
        AuthProviderPlugin provider = active();
        if (provider != null) {
            provider.endSession(config.context(), sessionId);
        }
    }

    /** Returns the backchannel receiver published by basis-auth, when present. */
    public BackchannelHandler backchannel() {
        if (config.context() == null) {
            return null;
        }
        Object service = config.context().service(BasisAuthPlugin.SERVICE_BACKCHANNEL);
        return service instanceof BackchannelHandler ? (BackchannelHandler) service : null;
    }
}
