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
public interface JahazielPlugin {
  /** Returns the plugin name. */
  default String name() {
    return getClass().getSimpleName();
  }

  /** Runs after the plugin loads. */
  default void onStartup() {}

  /** Runs before the context closes. */
  default void onShutdown() {}

  /** Authenticates a token, empty when this plugin has no opinion. */
  default Optional<UserIdentity> authenticate(String token) {
    return Optional.empty();
  }

  /** Overrides the gateway target for a workspace port, empty to keep the default. */
  default Optional<URI> gatewayTarget(UUID workspaceId, int port) {
    return Optional.empty();
  }

  /** Observes an administration action. */
  default void adminAction(String action, Map<String, String> context) {}
}
