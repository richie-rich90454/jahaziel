/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.identity.auth.token.ApiTokenConfig;
import dev.jahaziel.identity.auth.token.ApiTokenException;
import dev.jahaziel.identity.auth.token.ApiTokenService;
import dev.jahaziel.identity.auth.token.ApiTokenStore;
import dev.jahaziel.identity.auth.token.AuditListener;
import dev.jahaziel.identity.auth.token.TokenAudit;
import dev.jahaziel.identity.auth.token.TokenRecord;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Scoped, expiring API tokens. Tokens are never browser sessions. */
public final class ApiTokenPlugin extends AuthProviderPlugin {
    public static final String SERVICE_TOKENS = "api-token.service";
    public static final String SERVICE_AUDIT = "api-token.audit";
    public static final String SERVICE_AUDIT_LISTENER = "api-token.audit-listener";

    private static final PluginDescriptor DESCRIPTOR =
            new PluginDescriptor(
                    "api-token",
                    "1",
                    Collections.<String>emptyList(),
                    Arrays.asList(AuthCapability.API_TOKEN));

    private ApiTokenService service;
    private TokenAudit audit;

    public ApiTokenPlugin() {
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
        // API tokens have no browser redirect ceremony; they are issued through the service.
        return "";
    }

    @Override
    public AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters) {
        ensure(context);
        if (parameters == null) {
            throw new ApiTokenException("missing token parameter");
        }
        String token = parameters.get("token");
        if (token == null || token.length() == 0) {
            throw new ApiTokenException("missing token parameter");
        }
        TokenRecord record = service.verify(token);
        if (record == null) {
            throw new ApiTokenException("token is invalid or expired");
        }
        Map<String, String> claims = new HashMap<String, String>();
        claims.put("token_id", record.id());
        claims.put("scope", join(record.scopes()));
        return new AuthIdentity(
                "api-token",
                record.subject(),
                record.email(),
                record.displayName(),
                false,
                false,
                claims);
    }

    @Override
    public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
        ensure(context);
        if (sessionId != null && sessionId.length() > 0) {
            audit.record("deny", "browser-session-rejected");
        }
        return null;
    }

    @Override
    public void endSession(PluginContext context, String sessionId) {
        // No browser session state exists to tear down.
    }

    public ApiTokenService tokens() {
        return service;
    }

    public TokenAudit audit() {
        return audit;
    }

    private void ensure(PluginContext context) {
        if (audit == null) {
            Object injected = context.service(SERVICE_AUDIT);
            if (injected instanceof TokenAudit) {
                audit = (TokenAudit) injected;
            } else {
                audit = new TokenAudit();
                context.putService(SERVICE_AUDIT, audit);
            }
            Object listener = context.service(SERVICE_AUDIT_LISTENER);
            if (listener instanceof AuditListener) {
                audit.setListener((AuditListener) listener);
            }
        }
        if (service == null) {
            Object injected = context.service(SERVICE_TOKENS);
            if (injected instanceof ApiTokenService) {
                service = (ApiTokenService) injected;
            } else {
                ApiTokenConfig config = new ApiTokenConfig(context);
                service = new ApiTokenService(config, new ApiTokenStore(config), audit);
                context.putService(SERVICE_TOKENS, service);
            }
        }
    }

    private static String join(java.util.List<String> values) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                text.append(' ');
            }
            text.append(values.get(index));
        }
        return text.toString();
    }
}
