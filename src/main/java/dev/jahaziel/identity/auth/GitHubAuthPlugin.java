/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth;

import dev.jahaziel.identity.auth.basis.BasisHttp;
import dev.jahaziel.identity.auth.basis.Pkce;
import dev.jahaziel.identity.auth.github.GitHubConfig;
import dev.jahaziel.identity.auth.github.GitHubException;
import dev.jahaziel.identity.auth.github.GitHubHttp;
import dev.jahaziel.identity.auth.github.GitHubLoginCancelledException;
import dev.jahaziel.identity.auth.github.GitHubStore;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** GitHub OAuth2 provider: no ID token, identity comes from the GitHub user API. */
public final class GitHubAuthPlugin extends AuthProviderPlugin {
    public static final String SERVICE_STORE = "github-auth.store";

    private static final String PROVIDER = "github";

    private static final PluginDescriptor DESCRIPTOR = new PluginDescriptor(
            PROVIDER,
            "1",
            Collections.<String>emptyList(),
            Arrays.asList(AuthCapability.OAUTH2, AuthCapability.MFA_ASSURANCE));

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper mapper = new ObjectMapper();

    private GitHubConfig config;
    private GitHubHttp http;
    private GitHubStore store;

    public GitHubAuthPlugin() {
        super(DESCRIPTOR);
    }

    @Override
    public void initialize(PluginContext context) {
        config = new GitHubConfig(context);
        http = new GitHubHttp(HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build());
        store = new GitHubStore(config);
        context.putService(SERVICE_STORE, store);
    }

    @Override
    public void shutdown() {
    }

    @Override
    public String startLogin(PluginContext context, String state, String nonce, String redirectUri) {
        store.putPending(state, redirectUri);
        StringBuilder url = new StringBuilder(config.authorizeUrl());
        url.append(config.authorizeUrl().contains("?") ? '&' : '?');
        append(url, "client_id", config.clientId());
        append(url, "redirect_uri", redirectUri);
        append(url, "state", state);
        append(url, "scope", config.scopes());
        append(url, "response_type", "code");
        url.deleteCharAt(url.length() - 1);
        return url.toString();
    }

    @Override
    public AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters) {
        String state = parameters.get("state");
        GitHubStore.Pending pending = state == null ? null : store.takePending(state);
        if (pending == null) {
            throw new GitHubException("github state mismatch");
        }
        if ("access_denied".equals(parameters.get("error"))) {
            throw new GitHubLoginCancelledException();
        }
        String code = parameters.get("code");
        if (code == null || code.length() == 0) {
            throw new GitHubException("missing authorization code");
        }
        String accessToken = exchange(code, pending.redirectUri());
        GitHubHttp.Response userResponse = http.getJson(config.userUrl(), accessToken, HTTP_TIMEOUT);
        JsonNode user = parse(userResponse.body(), "github user response");
        JsonNode emails = parse(
                http.getJson(config.emailsUrl(), accessToken, HTTP_TIMEOUT).body(),
                "github emails response");

        EmailChoice email = chooseEmail(emails);
        String sessionId = Pkce.random();
        Map<String, String> claims = new HashMap<String, String>();
        claims.put("session_id", sessionId);
        AuthIdentity identity = new AuthIdentity(
                PROVIDER,
                subject(user),
                email.email(),
                firstText(user, "name", "login"),
                email.verified(),
                mfaObserved(userResponse, user),
                claims);
        store.putSession(sessionId, identity);
        store.putAccessToken(sessionId, accessToken);
        return identity;
    }

    @Override
    public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
        return store.getSession(sessionId);
    }

    @Override
    public void endSession(PluginContext context, String sessionId) {
        store.removeSession(sessionId);
    }

    private String exchange(String code, String redirectUri) {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", config.clientId());
        if (config.clientSecret() != null) {
            form.put("client_secret", config.clientSecret());
        }
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", redirectUri);
        JsonNode node = parse(
                http.postForm(config.tokenUrl(), BasisHttp.form(form), HTTP_TIMEOUT).body(),
                "github token response");
        JsonNode error = node.get("error");
        if (error != null && !error.isNull()) {
            throw new GitHubException("github token exchange was rejected");
        }
        String token = text(node, "access_token");
        if (token == null || token.length() == 0) {
            throw new GitHubException("github token response lacks access_token");
        }
        return token;
    }

    private JsonNode parse(String body, String label) {
        try {
            return mapper.readTree(body);
        } catch (Exception failure) {
            throw new GitHubException(label + " is not JSON", failure);
        }
    }

    private static String subject(JsonNode user) {
        JsonNode id = user == null ? null : user.get("id");
        if (id == null || id.isNull()) {
            throw new GitHubException("github user response lacks a numeric id");
        }
        return Long.toString(id.asLong());
    }

    private static String firstText(JsonNode node, String... fields) {
        for (int index = 0; index < fields.length; index++) {
            String value = text(node, fields[index]);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return "";
    }

    private static EmailChoice chooseEmail(JsonNode emails) {
        if (emails == null || !emails.isArray()) {
            return new EmailChoice("", false);
        }
        String best = "";
        boolean verified = false;
        int bestRank = -1;
        for (int index = 0; index < emails.size(); index++) {
            JsonNode entry = emails.get(index);
            String candidate = text(entry, "email");
            if (candidate == null || candidate.length() == 0) {
                continue;
            }
            boolean primary = entry.path("primary").asBoolean(false);
            boolean isVerified = entry.path("verified").asBoolean(false);
            int rank = primary && isVerified ? 3 : (isVerified ? 2 : (primary ? 1 : 0));
            if (rank > bestRank) {
                bestRank = rank;
                best = candidate;
                verified = isVerified;
            }
        }
        return new EmailChoice(best, verified);
    }

    private static boolean mfaObserved(GitHubHttp.Response response, JsonNode user) {
        String otp = response.header("X-GitHub-OTP");
        if (otp != null && otp.contains("required")) {
            return true;
        }
        return user.path("two_factor_authentication").asBoolean(false)
                || user.path("two_factor_enabled").asBoolean(false);
    }

    private static void append(StringBuilder url, String name, String value) {
        url.append(name).append('=').append(BasisHttp.encode(value)).append('&');
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static final class EmailChoice {
        private final String email;
        private final boolean verified;

        private EmailChoice(String email, boolean verified) {
            this.email = email;
            this.verified = verified;
        }

        private String email() {
            return email;
        }

        private boolean verified() {
            return verified;
        }
    }
}
