/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.identity.auth.basis.BackchannelHandler;
import dev.jahaziel.identity.auth.basis.BasisConfig;
import dev.jahaziel.identity.auth.basis.BasisException;
import dev.jahaziel.identity.auth.basis.BasisHttp;
import dev.jahaziel.identity.auth.basis.BasisLoginCancelledException;
import dev.jahaziel.identity.auth.basis.BasisMetadata;
import dev.jahaziel.identity.auth.basis.BasisOAuthException;
import dev.jahaziel.identity.auth.basis.BasisStore;
import dev.jahaziel.identity.auth.basis.Pkce;
import dev.jahaziel.identity.auth.basis.TokenVerifier;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** basis-auth OIDC provider: authorization code, PKCE, RS256 ID tokens, and backchannel events. */
public final class BasisAuthPlugin extends AuthProviderPlugin {
    public static final String SERVICE_BACKCHANNEL = "basis-auth.backchannel";
    public static final String SERVICE_TOKEN_VERIFIER = "basis-auth.token-verifier";
    public static final String SERVICE_STORE = "basis-auth.store";

    private static final PluginDescriptor DESCRIPTOR =
            new PluginDescriptor(
                    "basis-auth",
                    "1",
                    Collections.<String>emptyList(),
                    Arrays.asList(
                            AuthCapability.OIDC,
                            AuthCapability.OAUTH2,
                            AuthCapability.MFA_ASSURANCE,
                            AuthCapability.REFRESH,
                            AuthCapability.BACKCHANNEL));

    private final ObjectMapper mapper = new ObjectMapper();

    private PluginContext context;
    private BasisConfig config;
    private BasisMetadata metadata;
    private BasisStore store;
    private TokenVerifier verifier;
    private BackchannelHandler backchannel;
    private BasisHttp http;

    public BasisAuthPlugin() {
        super(DESCRIPTOR);
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        config = new BasisConfig(context);
        http =
                new BasisHttp(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        metadata = new BasisMetadata(config, http);
        store = new BasisStore(config);
        verifier = new TokenVerifier(config, metadata, store);
        backchannel = new BackchannelHandler(config, http, verifier, store);
        context.putService(SERVICE_BACKCHANNEL, backchannel);
        context.putService(SERVICE_TOKEN_VERIFIER, verifier);
        context.putService(SERVICE_STORE, store);
    }

    @Override
    public void shutdown() {}

    @Override
    public String startLogin(
            PluginContext context, String state, String nonce, String redirectUri) {
        String verifierValue = Pkce.random();
        store.putPending(state, nonce, verifierValue, redirectUri);
        BasisMetadata.Discovery discovery = metadata.discovery();
        StringBuilder url = new StringBuilder(discovery.authorizationEndpoint());
        url.append(discovery.authorizationEndpoint().contains("?") ? '&' : '?');
        append(url, "client_id", config.clientId());
        append(url, "response_type", "code");
        append(url, "redirect_uri", redirectUri);
        append(url, "scope", scope());
        append(url, "state", state);
        append(url, "nonce", nonce);
        append(url, "code_challenge", Pkce.challenge(verifierValue));
        append(url, "code_challenge_method", "S256");
        append(url, "resource", config.resource());
        url.deleteCharAt(url.length() - 1);
        return url.toString();
    }

    @Override
    public AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters) {
        String state = parameters.get("state");
        BasisStore.Pending pending = state == null ? null : store.takePending(state);
        if (pending == null) {
            throw new BasisException("basis-auth state mismatch");
        }
        if ("access_denied".equals(parameters.get("error"))) {
            throw new BasisLoginCancelledException();
        }
        String code = parameters.get("code");
        if (code == null || code.length() == 0) {
            throw new BasisException("missing authorization code");
        }
        Map<String, String> tokens = exchange(code, pending.verifier(), pending.redirectUri());
        TokenVerifier.VerifiedToken verified =
                verifier.verifyIdToken(
                        tokens.get("id_token"), pending.nonce(), tokens.get("access_token"));
        AuthIdentity derived = verifier.identity(verified);
        String sessionId = Pkce.random();
        Map<String, String> claims = new HashMap<String, String>(derived.claims());
        String email = derived.email();
        String displayName = derived.displayName();
        boolean emailVerified = derived.emailVerified();
        String access = tokens.get("access_token");
        if (access != null && access.length() > 0) {
            try {
                String body =
                        http.get(
                                metadata.discovery().userinfoEndpoint(),
                                "Bearer " + access,
                                Duration.ofSeconds(5));
                JsonNode profile = mapper.readTree(body);
                String profileEmail = text(profile, "email");
                String profileName = text(profile, "name");
                if (email == null || email.length() == 0) email = profileEmail;
                if (displayName == null || displayName.length() == 0) displayName = profileName;
                if (blank(claims.get("email"))
                        && profileEmail != null
                        && profileEmail.length() > 0) {
                    claims.put("email", profileEmail);
                }
                if (blank(claims.get("name")) && profileName != null && profileName.length() > 0) {
                    claims.put("name", profileName);
                }
                JsonNode verifiedFlag = profile == null ? null : profile.get("email_verified");
                if (!derived.claims().containsKey("email_verified")
                        && verifiedFlag != null
                        && verifiedFlag.isBoolean()) {
                    emailVerified = verifiedFlag.asBoolean();
                }
            } catch (Exception unavailable) {
                // UserInfo is optional profile enrichment; verified ID token claims stand.
            }
        }
        claims.put("session_id", sessionId);
        AuthIdentity identity =
                new AuthIdentity(
                        derived.provider(),
                        derived.subject(),
                        email,
                        displayName,
                        emailVerified,
                        derived.mfaAssured(),
                        claims);
        store.putSession(sessionId, identity);
        store.putRefreshToken(sessionId, tokens.get("refresh_token"));
        return identity;
    }

    @Override
    public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
        return store.getSession(sessionId);
    }

    @Override
    public AuthIdentity refresh(PluginContext context, String sessionId) {
        if (sessionId == null || sessionId.length() == 0) {
            return null;
        }
        String current = store.refreshToken(sessionId);
        if (current == null || current.length() == 0) {
            return null;
        }
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", current);
        form.put("resource", config.resource());
        if (config.publicClient()) {
            form.put("client_id", config.clientId());
        }
        String endpoint = metadata.discovery().tokenEndpoint();
        String authorization =
                config.publicClient() ? null : basic(config.clientId(), config.clientSecret());
        String body;
        try {
            body =
                    http.postForm(
                            endpoint, BasisHttp.form(form), authorization, Duration.ofSeconds(60));
        } catch (BasisOAuthException failure) {
            if ("invalid_grant".equals(failure.error())) {
                store.clearRefresh(sessionId);
                return null;
            }
            throw failure;
        }
        JsonNode node;
        try {
            node = mapper.readTree(body);
        } catch (Exception failure) {
            throw new BasisException("basis-auth refresh response is not JSON", failure);
        }
        String accessToken = text(node, "access_token");
        String idToken = text(node, "id_token");
        String rotated = text(node, "refresh_token");
        if (accessToken == null || idToken == null || rotated == null || rotated.length() == 0) {
            throw new BasisException("basis-auth refresh response lacks required tokens");
        }
        long expiresIn =
                node == null || node.get("expires_in") == null
                        ? 0L
                        : node.get("expires_in").asLong();
        store.consumeAndReplace(sessionId, current, rotated);
        store.putTokenExpiresAt(sessionId, System.currentTimeMillis() + expiresIn * 1000L);
        return store.getSession(sessionId);
    }

    /** The context handed to initialize, for callers without direct access to it. */
    public PluginContext context() {
        return context;
    }

    @Override
    public void endSession(PluginContext context, String sessionId) {
        store.removeSession(sessionId);
    }

    public BackchannelHandler backchannel() {
        return backchannel;
    }

    private String scope() {
        StringBuilder scope = new StringBuilder("openid profile email");
        if (config.scopes().length() > 0) {
            scope.append(' ').append(config.scopes());
        }
        if (config.offlineAccess()) {
            scope.append(" offline_access");
        }
        return scope.toString();
    }

    private Map<String, String> exchange(String code, String codeVerifier, String redirectUri) {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("code_verifier", codeVerifier);
        form.put("resource", config.resource());
        if (config.publicClient()) {
            form.put("client_id", config.clientId());
        }
        String endpoint = metadata.discovery().tokenEndpoint();
        String authorization =
                config.publicClient() ? null : basic(config.clientId(), config.clientSecret());
        String body =
                http.postForm(
                        endpoint, BasisHttp.form(form), authorization, Duration.ofSeconds(60));
        JsonNode node;
        try {
            node = mapper.readTree(body);
        } catch (Exception failure) {
            throw new BasisException("basis-auth token response is not JSON", failure);
        }
        String idToken = text(node, "id_token");
        String accessToken = text(node, "access_token");
        if (idToken == null || accessToken == null) {
            throw new BasisException("basis-auth token response lacks id_token or access_token");
        }
        Map<String, String> tokens = new HashMap<String, String>();
        tokens.put("id_token", idToken);
        tokens.put("access_token", accessToken);
        tokens.put("refresh_token", text(node, "refresh_token"));
        return tokens;
    }

    private static void append(StringBuilder url, String name, String value) {
        url.append(name).append('=').append(BasisHttp.encode(value)).append('&');
    }

    private static boolean blank(String value) {
        return value == null || value.length() == 0;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String basic(String user, String secret) {
        String pair = user + ":" + secret;
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }
}
