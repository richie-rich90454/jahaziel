/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.identity.auth.ldap.JndiLdapGateway;
import dev.jahaziel.identity.auth.ldap.LdapConfig;
import dev.jahaziel.identity.auth.ldap.LdapException;
import dev.jahaziel.identity.auth.ldap.LdapGateway;
import dev.jahaziel.identity.auth.ldap.LdapStore;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** LDAP bind authentication provider. Sessions are stored under the data directory. */
public final class LdapPlugin extends AuthProviderPlugin {
    public static final String SERVICE_GATEWAY = "ldap.gateway";
    public static final String SERVICE_STORE = "ldap.store";
    public static final String SERVICE_CONFIG = "ldap.config";

    private static final PluginDescriptor DESCRIPTOR =
            new PluginDescriptor(
                    "ldap",
                    "1",
                    Collections.<String>emptyList(),
                    Arrays.asList(AuthCapability.PASSWORD, AuthCapability.MFA_ASSURANCE));

    private final SecureRandom random = new SecureRandom();
    private LdapConfig config;
    private LdapGateway gateway;
    private LdapStore store;

    public LdapPlugin() {
        super(DESCRIPTOR);
    }

    @Override
    public void initialize(PluginContext context) {
        ensure(context);
    }

    @Override
    public void shutdown() {}

    @Override
    public String startLogin(
            PluginContext context, String state, String nonce, String redirectUri) {
        // LDAP authenticates credentials directly; there is no redirect ceremony.
        return "";
    }

    @Override
    public AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters) {
        ensure(context);
        if (parameters == null) {
            throw new LdapException("missing credentials");
        }
        String username = parameters.get("username");
        String password = parameters.get("password");
        if (username == null
                || username.length() == 0
                || password == null
                || password.length() == 0) {
            throw new LdapException("missing credentials");
        }
        AuthIdentity derived = gateway.authenticate(config, username, password);
        String sessionId = sessionId();
        Map<String, String> claims = new HashMap<String, String>(derived.claims());
        claims.put("session_id", sessionId);
        AuthIdentity identity =
                new AuthIdentity(
                        derived.provider(),
                        derived.subject(),
                        derived.email(),
                        derived.displayName(),
                        derived.emailVerified(),
                        derived.mfaAssured(),
                        claims);
        store.putSession(sessionId, identity);
        return identity;
    }

    @Override
    public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
        ensure(context);
        if (sessionId == null || sessionId.length() == 0) {
            return null;
        }
        return store.getSession(sessionId);
    }

    @Override
    public void endSession(PluginContext context, String sessionId) {
        ensure(context);
        if (sessionId != null) {
            store.removeSession(sessionId);
        }
    }

    private String sessionId() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void ensure(PluginContext context) {
        if (config == null) {
            config = new LdapConfig(context);
        }
        if (gateway == null) {
            Object injected = context.service(SERVICE_GATEWAY);
            if (injected instanceof LdapGateway) {
                gateway = (LdapGateway) injected;
            } else {
                gateway = new JndiLdapGateway();
                context.putService(SERVICE_GATEWAY, gateway);
            }
        }
        if (store == null) {
            Object injected = context.service(SERVICE_STORE);
            if (injected instanceof LdapStore) {
                store = (LdapStore) injected;
            } else {
                store = new LdapStore(config);
                context.putService(SERVICE_STORE, store);
            }
        }
        context.putService(SERVICE_CONFIG, config);
    }
}
