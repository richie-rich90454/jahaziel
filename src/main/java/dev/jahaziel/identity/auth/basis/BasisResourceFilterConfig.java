/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import dev.jahaziel.identity.auth.AuthPluginRegistry;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.net.http.HttpClient;
import java.time.Duration;

/** Wires the basis-auth resource filter only when the basis-auth provider is configured. */
@Configuration
public class BasisResourceFilterConfig {
    /**
     * Registers the filter ahead of the app security chain but only for workspace bearer calls. The
     * registry argument forces plugin initialization before the verifier service is looked up.
     */
    @Bean
    public FilterRegistrationBean<BasisResourceFilter> basisResourceFilter(
            PluginContext context, AuthPluginRegistry registry) {
        Object service = context.service(BasisAuthPlugin.SERVICE_TOKEN_VERIFIER);
        if (!(service instanceof TokenVerifier)) {
            // A @Bean method must never return null; return a disabled registration instead so
            // deployments without basis-auth keep the normal login and security chain.
            FilterRegistrationBean<BasisResourceFilter> disabled =
                    new FilterRegistrationBean<BasisResourceFilter>(
                            new BasisResourceFilter(
                                    null, new BasisScopeGuard(), new String[0], new String[0]));
            disabled.setEnabled(false);
            return disabled;
        }
        BasisConfig config = new BasisConfig(context);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        BasisHttp http = new BasisHttp(client);
        BasisMetadata metadata = new BasisMetadata(config, http);
        Object stored = context.service(BasisAuthPlugin.SERVICE_STORE);
        BasisStore store =
                stored instanceof BasisStore ? (BasisStore) stored : new BasisStore(config);
        BasisInternalClient subjects = new BasisInternalClient(config, http, client);
        TokenVerifier verifier = new TokenVerifier(config, metadata, store, subjects);
        BasisResourceFilter filter =
                new BasisResourceFilter(
                        verifier,
                        new BasisScopeGuard(),
                        words(config.scopes()),
                        words(
                                first(
                                        context,
                                        "basis.permissions",
                                        "BASIS_AUTH_PERMISSIONS",
                                        "BASIS_PERMISSIONS")));
        FilterRegistrationBean<BasisResourceFilter> registration =
                new FilterRegistrationBean<BasisResourceFilter>(filter);
        registration.addUrlPatterns("/api/v1/workspaces/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
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

    private static String[] words(String value) {
        if (value == null || value.trim().length() == 0) {
            return new String[0];
        }
        return value.trim().split("\\s+");
    }
}
