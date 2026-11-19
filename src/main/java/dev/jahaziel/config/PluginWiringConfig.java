/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import dev.jahaziel.identity.auth.ApiTokenPlugin;
import dev.jahaziel.identity.auth.AuthPluginRegistry;
import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.identity.auth.GenericOidcPlugin;
import dev.jahaziel.identity.auth.GitHubAuthPlugin;
import dev.jahaziel.identity.auth.GoogleAuthPlugin;
import dev.jahaziel.identity.auth.LdapPlugin;
import dev.jahaziel.identity.auth.LocalPasswordPlugin;
import dev.jahaziel.identity.auth.MicrosoftAuthPlugin;
import dev.jahaziel.identity.auth.PasskeyPlugin;
import dev.jahaziel.networking.internal.LinuxPortDiscovery;
import dev.jahaziel.networking.internal.MacPortDiscovery;
import dev.jahaziel.networking.internal.WindowsPortDiscovery;
import dev.jahaziel.ops.GracefulDrainService;
import dev.jahaziel.ops.OpsShutdownHook;
import dev.jahaziel.platform.spi.LinuxProcessEnvironment;
import dev.jahaziel.platform.spi.MacProcessEnvironment;
import dev.jahaziel.platform.spi.WindowsProcessEnvironment;
import dev.jahaziel.plugin.PluginContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.util.List;

/** Registers concrete plugins as beans and assembles the gated auth-provider registry. */
@Configuration
public class PluginWiringConfig {
    private static final String[] PRIORITY = {
        "basis-auth",
        "microsoft",
        "google",
        "github",
        "oidc",
        "local",
        "passkey",
        "api-token",
        "ldap"
    };

    /** Returns the gate that skips auth providers whose required settings are absent. */
    @Bean
    public AuthProviderGate authProviderGate() {
        return new AuthProviderGate();
    }

    /** Returns the shared settings and data directory handed to every plugin. */
    @Bean
    public PluginContext pluginContext(
            @Value("${jahaziel.storage.root}") String storageRoot, Environment environment) {
        return new PluginContext(Path.of(storageRoot), PluginSettings.from(environment));
    }

    @Bean
    public LinuxProcessEnvironment linuxProcessEnvironment() {
        return new LinuxProcessEnvironment();
    }

    @Bean
    public MacProcessEnvironment macProcessEnvironment() {
        return new MacProcessEnvironment();
    }

    @Bean
    public WindowsProcessEnvironment windowsProcessEnvironment() {
        return new WindowsProcessEnvironment();
    }

    @Bean
    public LinuxPortDiscovery linuxPortDiscovery() {
        return new LinuxPortDiscovery();
    }

    @Bean
    public MacPortDiscovery macPortDiscovery() {
        return new MacPortDiscovery();
    }

    @Bean
    public WindowsPortDiscovery windowsPortDiscovery() {
        return new WindowsPortDiscovery();
    }

    @Bean
    public BasisAuthPlugin basisAuth() {
        return new BasisAuthPlugin();
    }

    @Bean
    public MicrosoftAuthPlugin microsoftAuth() {
        return new MicrosoftAuthPlugin();
    }

    @Bean
    public GoogleAuthPlugin googleAuth() {
        return new GoogleAuthPlugin();
    }

    @Bean
    public GitHubAuthPlugin githubAuth() {
        return new GitHubAuthPlugin();
    }

    @Bean
    public GenericOidcPlugin oidcAuth() {
        return new GenericOidcPlugin();
    }

    @Bean
    public LocalPasswordPlugin localAuth() {
        return new LocalPasswordPlugin();
    }

    @Bean
    public PasskeyPlugin passkeyAuth() {
        return new PasskeyPlugin();
    }

    @Bean
    public ApiTokenPlugin apiTokenAuth() {
        return new ApiTokenPlugin();
    }

    @Bean
    public LdapPlugin ldapAuth() {
        return new LdapPlugin();
    }

    /** Registers only configured providers, in priority order, and initializes just those. */
    @Bean(destroyMethod = "shutdownAll")
    public AuthPluginRegistry authPluginRegistry(
            PluginContext pluginContext,
            AuthProviderGate gate,
            List<AuthProviderPlugin> providers) {
        AuthPluginRegistry registry = new AuthPluginRegistry();
        for (int priority = 0; priority < PRIORITY.length; priority++) {
            AuthProviderPlugin provider = find(providers, PRIORITY[priority]);
            if (provider == null || !gate.ready(pluginContext, provider)) {
                continue;
            }
            registry.register(provider);
        }
        List<AuthProviderPlugin> enabled = registry.all();
        for (int index = 0; index < enabled.size(); index++) {
            enabled.get(index).initialize(pluginContext);
        }
        return registry;
    }

    /** Returns the drain hook driven by the registrar at JVM shutdown. */
    @Bean
    public OpsShutdownHook opsShutdownHook(
            GracefulDrainService drain,
            @Value("${jahaziel.ops.drain-timeout-ms:15000}") long timeoutMillis) {
        return new OpsShutdownHook(drain, timeoutMillis);
    }

    /** Attaches the drain hook with the JVM and detaches it with the Spring context. */
    @Bean(initMethod = "register", destroyMethod = "unregister")
    public OpsShutdownHookRegistrar opsShutdownHookRegistrar(OpsShutdownHook hook) {
        return new OpsShutdownHookRegistrar(hook);
    }

    private static AuthProviderPlugin find(List<AuthProviderPlugin> providers, String name) {
        for (int index = 0; index < providers.size(); index++) {
            AuthProviderPlugin provider = providers.get(index);
            if (name.equals(provider.descriptor().name())) {
                return provider;
            }
        }
        return null;
    }
}
