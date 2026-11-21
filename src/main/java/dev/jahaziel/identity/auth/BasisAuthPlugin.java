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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final long MIN_REFRESH_WINDOW_SECONDS = 1L;
    private static final long MAX_REFRESH_WINDOW_SECONDS = 300L;
    private static final Logger log = LoggerFactory.getLogger(BasisAuthPlugin.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();

    private PluginContext context;
    private BasisConfig config;
    private BasisMetadata metadata;
    private BasisStore store;
    private TokenVerifier verifier;
    private BackchannelHandler backchannel;
    private BasisHttp http;
    private long refreshWindowMillis;
    private ScheduledExecutorService refresher;

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
        refreshWindowMillis = configuredRefreshWindow(context);
        if (refreshWindowMillis > 0L && config.offlineAccess()) {
            long intervalSeconds = Math.max(1L, refreshWindowMillis / 2000L);
            refresher = Executors.newSingleThreadScheduledExecutor(new RefreshThreads());
            refresher.scheduleWithFixedDelay(
                    new Runnable() {
                        @Override
                        public void run() {
                            refreshDue();
                        }
                    },
                    intervalSeconds,
                    intervalSeconds,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public void shutdown() {
        if (refresher != null) {
            refresher.shutdownNow();
            refresher = null;
        }
    }

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
        long expiresIn = seconds(tokens.get("expires_in"));
        if (expiresIn > 0L) {
            store.putTokenExpiresAt(sessionId, System.currentTimeMillis() + expiresIn * 1000L);
        }
        return identity;
    }

    @Override
    public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
        return store.getSession(sessionId);
    }

    /**
     * Refreshes a session, skipping duplicate calls for a session already being rotated. Two
     * concurrent refreshes would make the issuer revoke the whole token family.
     */
    @Override
    public AuthIdentity refresh(PluginContext context, String sessionId) {
        if (sessionId == null || sessionId.length() == 0 || !refreshing.add(sessionId)) {
            return null;
        }
        try {
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
                                endpoint,
                                BasisHttp.form(form),
                                authorization,
                                Duration.ofSeconds(60));
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
            if (accessToken == null
                    || idToken == null
                    || rotated == null
                    || rotated.length() == 0) {
                throw new BasisException("basis-auth refresh response lacks required tokens");
            }
            long expiresIn =
                    node == null || node.get("expires_in") == null
                            ? 0L
                            : node.get("expires_in").asLong();
            store.consumeAndReplace(sessionId, current, rotated);
            store.putTokenExpiresAt(sessionId, System.currentTimeMillis() + expiresIn * 1000L);
            return store.getSession(sessionId);
        } finally {
            refreshing.remove(sessionId);
        }
    }

    /**
     * Runs one pre-expiry refresh pass over due sessions. Public so a lifecycle test can drive a
     * pass deterministically instead of waiting for the scheduler's tick.
     */
    public void refreshDue() {
        if (refreshWindowMillis <= 0L) {
            return;
        }
        List<String> due =
                store.sessionsNeedingRefresh(System.currentTimeMillis(), refreshWindowMillis);
        for (String sessionId : due) {
            try {
                refresh(context, sessionId);
            } catch (RuntimeException failure) {
                // One failed session must not stop the pass or expose tokens in logs.
                log.warn("basis-auth automatic refresh failed for a session");
            }
        }
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
        JsonNode expires = node == null ? null : node.get("expires_in");
        tokens.put(
                "expires_in",
                expires == null || expires.isNull() ? null : Long.toString(expires.asLong()));
        return tokens;
    }

    /** Opt-in window in milliseconds; zero disables the scheduler. Invalid values fail closed. */
    private static long configuredRefreshWindow(PluginContext context) {
        String value = context.setting("basis.refresh-window-seconds");
        if (blank(value)) {
            value = context.setting("BASIS_AUTH_REFRESH_WINDOW_SECONDS");
        }
        if (blank(value)) {
            value = System.getenv("BASIS_AUTH_REFRESH_WINDOW_SECONDS");
        }
        if (blank(value)) {
            return 0L;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds >= MIN_REFRESH_WINDOW_SECONDS && seconds <= MAX_REFRESH_WINDOW_SECONDS) {
                return seconds * 1000L;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the warning and stay off.
        }
        log.warn(
                "basis-auth refresh window must be {} to {} seconds; automatic refresh is off",
                Long.valueOf(MIN_REFRESH_WINDOW_SECONDS),
                Long.valueOf(MAX_REFRESH_WINDOW_SECONDS));
        return 0L;
    }

    private static long seconds(String value) {
        if (blank(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
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

    /** One daemon thread for the whole plugin, stopped by shutdown. */
    private static final class RefreshThreads implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "basis-refresh-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
