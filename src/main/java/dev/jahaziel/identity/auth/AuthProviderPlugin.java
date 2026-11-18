/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.plugin.Plugin;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import java.util.Map;

/** Contract for an authentication provider. Abstract class, no interface keyword. */
public abstract class AuthProviderPlugin extends Plugin {
    protected AuthProviderPlugin(PluginDescriptor descriptor) {
        super(descriptor);
    }

    public abstract String startLogin(
            PluginContext context, String state, String nonce, String redirectUri);

    public abstract AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters);

    public abstract AuthIdentity currentIdentity(PluginContext context, String sessionId);

    public abstract void endSession(PluginContext context, String sessionId);

    public boolean supports(String capability) {
        return descriptor().provides(capability);
    }
}
