/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.api;

import dev.jahaziel.spi.UserIdentity;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Plugin hooks: lifecycle, authentication, gateway routing, administration. Discovered from {@code
 * data/plugins/*.jar} via {@code ServiceLoader}. Every hook defaults to no opinion.
 */
public abstract class JahazielPlugin {
    /** Returns the plugin name. */
    public String name() {
        return getClass().getSimpleName();
    }

    /** Runs after the plugin loads. */
    public void onStartup() {}

    /** Runs before the context closes. */
    public void onShutdown() {}

    /** Authenticates a token, empty when this plugin has no opinion. */
    public Optional<UserIdentity> authenticate(String token) {
        return Optional.empty();
    }

    /** Overrides the gateway target for a workspace port, empty to keep the default. */
    public Optional<URI> gatewayTarget(UUID workspaceId, int port) {
        return Optional.empty();
    }

    /** Observes an administration action. */
    public void adminAction(String action, Map<String, String> context) {}
}
