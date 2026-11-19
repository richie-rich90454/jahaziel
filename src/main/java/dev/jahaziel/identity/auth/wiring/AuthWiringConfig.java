/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.wiring;

import dev.jahaziel.identity.auth.AuthPluginRegistry;
import dev.jahaziel.plugin.PluginContext;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bridges the already-registered auth plugins into the login flow. The plugin context and registry
 * come from PluginWiringConfig; this class only adds the dispatcher and identity link store.
 */
@Configuration
public class AuthWiringConfig {
    /** Reads the ordered list of enabled providers from the shared plugin context. */
    @Bean
    public AuthProviderConfig authProviderConfig(
            PluginContext pluginContext, AuthPluginRegistry authPluginRegistry) {
        return AuthProviderConfig.fromSettings(pluginContext, authPluginRegistry);
    }

    /** Selects the active provider, or allows the DevConnect fallback when none is configured. */
    @Bean
    public AuthProviderDispatcher authProviderDispatcher(
            AuthPluginRegistry authPluginRegistry, AuthProviderConfig authProviderConfig) {
        return new AuthProviderDispatcher(authPluginRegistry, authProviderConfig);
    }

    /** Stores the durable (provider, subject) to user links. */
    @Bean
    public UserIdentityStore userIdentityStore(JdbcTemplate jdbcTemplate) {
        return new UserIdentityStore(jdbcTemplate);
    }
}
