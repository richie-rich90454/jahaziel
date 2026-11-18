/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.oidc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared OIDC authorization-code flow. Discovery and keys are cached for five minutes;
 * pending logins and sessions are persisted beneath the plugin data directory.
 */
public abstract class OidcProviderPlugin extends AuthProviderPlugin {
    private static final long TTL_MILLIS = 5L * 60L * 1000L;
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TOKEN_TIMEOUT = Duration.ofSeconds(60);
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    protected PluginContext context;
    private String issuer;
    private String clientId;
    private String clientSecret;
    private String scopes;
    private String redirectUri;
    private OidcStore store;

    private volatile Discovery discovery;
    private volatile long discoveryAt;
    private volatile JWKSet keys;
    private volatile long keysAt;

    protected OidcProviderPlugin(PluginDescriptor descriptor) {
        super(descriptor);
    }

    /** Stable provider name used as the identity namespace and store file name. */
    protected abstract String providerName();

    /** Prefix for plugin settings, for example "google" reads "google.client-id". */
    protected abstract String configPrefix();

    /** Issuer used when no setting is present. Null means the setting is required. */
    protected String defaultIssuer() {
        return null;
    }

    /** Extra scopes appended to the mandatory openid, profile and email scopes. */
    protected String defaultScopes() {
        return "";
    }

    /** Issuer comparison hook for providers with tenant-templated issuers. */
    protected boolean issuerMatches(String expected, String actual) {
        return expected.equals(actual);
    }

    /** Claims copied onto the identity. Override to expose provider-specific claims. */
    protected Map<String, String> mapClaims(JWTClaimsSet claims) {
        Map<String, String> safe = new HashMap<String, String>();
        copyClaim(safe, claims, "sub");
        copyClaim(safe, claims, "email");
        copyClaim(safe, claims, "name");
        copyClaim(safe, claims, "email_verified");
        copyClaim(safe, claims, "acr");
        copyClaim(safe, claims, "amr");
        copyClaim(safe, claims, "iss");
        copyClaim(safe, claims, "aud");
        return safe;
    }

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        this.issuer = configuredIssuer();
        this.clientId = requiredSetting(prefix() + ".client-id", env(prefix(), "CLIENT_ID"));
        this.clientSecret = firstSetting(prefix() + ".client-secret", env(prefix(), "CLIENT_SECRET"));
        this.scopes = buildScopes();
        this.redirectUri = firstSetting(prefix() + ".redirect-uri", env(prefix(), "REDIRECT_URI"));
        this.store = new OidcStore(context.dataDir(), providerName());
        context.putService("oidc." + providerName() + ".store", store);
    }

    @Override
    public void shutdown() {
    }

    @Override
    public String startLogin(PluginContext context, String state, String nonce, String redirectUri) {
        requireText(state, "state");
        requireText(nonce, "nonce");
        String callback = redirectUri == null || redirectUri.length() == 0 ? this.redirectUri : redirectUri;
        requireText(callback, "redirect URI");
        String verifier = randomValue();
        store.putPending(state, nonce, verifier, callback);
        Discovery found = discovery();
        StringBuilder url = new StringBuilder(found.authorizationEndpoint());
        url.append(found.authorizationEndpoint().contains("?") ? '&' : '?');
        append(url, "response_type", "code");
        append(url, "client_id", clientId);
        append(url, "redirect_uri", callback);
        append(url, "scope", scopes);
        append(url, "state", state);
        append(url, "nonce", nonce);
        append(url, "code_challenge", challenge(verifier));
        append(url, "code_challenge_method", "S256");
        url.deleteCharAt(url.length() - 1);
        return url.toString();
    }

    @Override
    public AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters) {
        String state = parameters == null ? null : parameters.get("state");
        OidcStore.Pending pending = state == null ? null : store.takePending(state);
        if (pending == null) {
            throw new OidcException(providerName() + " state mismatch");
        }
        if ("access_denied".equals(parameters.get("error"))) {
            throw new OidcException.Cancelled(providerName());
        }
        String code = parameters.get("code");
        requireText(code, "authorization code");
        Map<String, String> tokens = exchange(code, pending.verifier(), pending.redirectUri());
        JWTClaimsSet claims = verifyIdToken(tokens.get("id_token"), pending.nonce(), tokens.get("access_token"));
        AuthIdentity derived = identity(claims);
        String sessionId = randomValue();
        Map<String, String> claimsWithSession = new HashMap<String, String>(derived.claims());
        claimsWithSession.put("session_id", sessionId);
        AuthIdentity identity = new AuthIdentity(
                derived.provider(),
                derived.subject(),
                derived.email(),
                derived.displayName(),
                derived.emailVerified(),
                derived.mfaAssured(),
                claimsWithSession);
        store.putSession(sessionId, identity);
        return identity;
    }

    @Override
    public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
        return sessionId == null ? null : store.getSession(sessionId);
    }

    @Override
    public void endSession(PluginContext context, String sessionId) {
        if (sessionId != null) {
            store.removeSession(sessionId);
        }
    }

    /** MFA is assured by a multi-factor amr entry or an MFA-bearing acr value. */
    protected static boolean mfaAssured(JWTClaimsSet claims) {
        List<String> methods = stringList(claims, "amr");
        if (methods != null) {
            for (int index = 0; index < methods.size(); index++) {
                String method = methods.get(index);
                if ("mfa".equalsIgnoreCase(method) || "otp".equalsIgnoreCase(method)) {
                    return true;
                }
            }
        }
        String acr = stringClaim(claims, "acr");
        if (acr != null) {
            String normalized = acr.toLowerCase(Locale.ROOT);
            if ("2".equals(acr) || normalized.contains("mfa")) {
                return true;
            }
        }
        return false;
    }

    private String configuredIssuer() {
        String value = firstSetting(prefix() + ".issuer", env(prefix(), "ISSUER"));
        if (value == null || value.length() == 0) {
            value = defaultIssuer();
        }
        if (value == null || value.length() == 0) {
            throw new OidcException("missing " + prefix() + " issuer");
        }
        return value;
    }

    private String buildScopes() {
        StringBuilder value = new StringBuilder("openid profile email");
        String extra = firstSetting(prefix() + ".scopes", env(prefix(), "SCOPES"));
        if (extra != null && extra.trim().length() > 0) {
            value.append(' ').append(extra.trim());
        }
        String defaults = defaultScopes();
        if (defaults != null && defaults.trim().length() > 0) {
            value.append(' ').append(defaults.trim());
        }
        return value.toString();
    }

    private String prefix() {
        return configPrefix();
    }

    private static String env(String prefix, String suffix) {
        return prefix.toUpperCase(Locale.ROOT) + "_" + suffix;
    }

    private String firstSetting(String... keys) {
        for (int index = 0; index < keys.length; index++) {
            String value = context.setting(keys[index]);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return null;
    }

    private String requiredSetting(String... keys) {
        String value = firstSetting(keys);
        if (value == null) {
            throw new OidcException("missing " + prefix() + " setting " + keys[0]);
        }
        return value;
    }

    private Map<String, String> exchange(String code, String verifier, String redirectUri) {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("code_verifier", verifier);
        boolean confidential = clientSecret != null && clientSecret.length() > 0;
        String authorization = confidential ? basic(clientId, clientSecret) : null;
        if (!confidential) {
            form.put("client_id", clientId);
        }
        String body = postForm(discovery().tokenEndpoint(), encodeForm(form), authorization);
        JsonNode node;
        try {
            node = mapper.readTree(body);
        } catch (JacksonException failure) {
            throw new OidcException(providerName() + " token response is not JSON", failure);
        }
        String idToken = text(node, "id_token");
        String accessToken = text(node, "access_token");
        requireText(idToken, "id_token");
        requireText(accessToken, "access_token");
        Map<String, String> tokens = new HashMap<String, String>();
        tokens.put("id_token", idToken);
        tokens.put("access_token", accessToken);
        tokens.put("refresh_token", text(node, "refresh_token"));
        return tokens;
    }

    private JWTClaimsSet verifyIdToken(String token, String expectedNonce, String accessToken) {
        SignedJWT jwt = parse(token);
        if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
            throw new TokenRejectedException("unsupported ID token algorithm");
        }
        JWSVerifier verifier = verifierFor(jwt.getHeader().getKeyID());
        try {
            if (!jwt.verify(verifier)) {
                throw new TokenRejectedException("bad ID token signature");
            }
        } catch (JOSEException failure) {
            throw new TokenRejectedException("ID token signature check failed");
        }
        JWTClaimsSet claims = claims(jwt);
        if (!issuerMatches(issuer, claims.getIssuer())) {
            throw new TokenRejectedException("wrong ID token issuer");
        }
        List<String> audiences = claims.getAudience();
        if (audiences == null || !audiences.contains(clientId)) {
            throw new TokenRejectedException("wrong ID token audience");
        }
        Date expiry = claims.getExpirationTime();
        if (expiry == null || !expiry.after(new Date())) {
            throw new TokenRejectedException("expired ID token");
        }
        String nonce = stringClaim(claims, "nonce");
        if (expectedNonce == null || !expectedNonce.equals(nonce)) {
            throw new TokenRejectedException("ID token nonce mismatch");
        }
        String hash = stringClaim(claims, "at_hash");
        if (hash != null && (accessToken == null || !hash.equals(atHash(accessToken)))) {
            throw new TokenRejectedException("ID token access token hash mismatch");
        }
        return claims;
    }

    private AuthIdentity identity(JWTClaimsSet claims) {
        String subject = stringClaim(claims, "sub");
        requireText(subject, "ID token sub");
        return new AuthIdentity(
                providerName(),
                subject,
                stringClaim(claims, "email"),
                stringClaim(claims, "name"),
                booleanClaim(claims, "email_verified"),
                mfaAssured(claims),
                mapClaims(claims));
    }

    private Discovery discovery() {
        Discovery found = discovery;
        if (found != null && fresh(discoveryAt)) {
            return found;
        }
        synchronized (this) {
            if (discovery == null || !fresh(discoveryAt)) {
                discovery = loadDiscovery();
                discoveryAt = System.currentTimeMillis();
            }
            return discovery;
        }
    }

    private Discovery loadDiscovery() {
        String url = issuer + "/.well-known/openid-configuration";
        String body = get(url);
        JsonNode node;
        try {
            node = mapper.readTree(body);
        } catch (JacksonException failure) {
            throw new OidcException(providerName() + " discovery is not JSON", failure);
        }
        Discovery loaded = new Discovery(
                required(node, "issuer", url),
                required(node, "authorization_endpoint", url),
                required(node, "token_endpoint", url),
                required(node, "jwks_uri", url));
        if (!issuerMatches(issuer, loaded.issuer())) {
            throw new OidcException(providerName() + " discovery issuer does not match configuration");
        }
        return loaded;
    }

    private JWSVerifier verifierFor(String keyId) {
        JWKSet set = jwks(false);
        JWK key = find(set, keyId);
        if (key == null) {
            set = jwks(true);
            key = find(set, keyId);
        }
        if (!(key instanceof RSAKey)) {
            throw new TokenRejectedException("JWKS has no usable signing key");
        }
        try {
            return new RSASSAVerifier(((RSAKey) key).toRSAPublicKey());
        } catch (JOSEException failure) {
            throw new TokenRejectedException("signing key is not RSA");
        }
    }

    private JWKSet jwks(boolean force) {
        JWKSet found = keys;
        if (!force && found != null && fresh(keysAt)) {
            return found;
        }
        synchronized (this) {
            if (force || keys == null || !fresh(keysAt)) {
                String url = discovery().jwksUri();
                String body = get(url);
                try {
                    keys = JWKSet.parse(body);
                } catch (ParseException failure) {
                    throw new OidcException(providerName() + " JWKS is not valid JSON", failure);
                }
                keysAt = System.currentTimeMillis();
            }
            return keys;
        }
    }

    private static JWK find(JWKSet set, String keyId) {
        if (set == null) {
            return null;
        }
        if (keyId == null) {
            for (int index = 0; index < set.getKeys().size(); index++) {
                JWK candidate = set.getKeys().get(index);
                if (candidate instanceof RSAKey) {
                    return candidate;
                }
            }
            return null;
        }
        JWK key = set.getKeyByKeyId(keyId);
        return key instanceof RSAKey ? key : null;
    }

    private static boolean fresh(long fetchedAt) {
        return System.currentTimeMillis() - fetchedAt < TTL_MILLIS;
    }

    private String get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(FETCH_TIMEOUT)
                .GET()
                .build();
        return send(request, url);
    }

    private String postForm(String url, String body, String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(TOKEN_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return send(builder.build(), url);
    }

    private String send(HttpRequest request, String url) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new OidcException(providerName() + " HTTP " + status + " at " + url);
            }
            return response.body();
        } catch (IOException failure) {
            throw new OidcException(providerName() + " request failed for " + url, failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new OidcException(providerName() + " request interrupted", failure);
        }
    }

    private static void append(StringBuilder url, String name, String value) {
        url.append(name).append('=').append(encode(value)).append('&');
    }

    private static String encodeForm(Map<String, String> values) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return body.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String basic(String user, String secret) {
        String pair = user + ":" + secret;
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String challenge(String verifier) {
        return URL_ENCODER.encodeToString(sha256(verifier));
    }

    private static String atHash(String accessToken) {
        byte[] digest = sha256(accessToken);
        byte[] half = new byte[16];
        System.arraycopy(digest, 0, half, 0, half.length);
        return URL_ENCODER.encodeToString(half);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException impossible) {
            throw new OidcException("SHA-256 is unavailable", impossible);
        }
    }

    private static SignedJWT parse(String token) {
        requireText(token, "ID token");
        try {
            return SignedJWT.parse(token);
        } catch (ParseException failure) {
            throw new TokenRejectedException("malformed ID token");
        }
    }

    private static JWTClaimsSet claims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException failure) {
            throw new TokenRejectedException("malformed ID token claims");
        }
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException failure) {
            throw new TokenRejectedException("malformed ID token claim " + name);
        }
    }

    private static boolean booleanClaim(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    private static List<String> stringList(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringListClaim(name);
        } catch (ParseException failure) {
            throw new TokenRejectedException("malformed ID token claim " + name);
        }
    }

    private static void copyClaim(Map<String, String> target, JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        if (value == null) {
            return;
        }
        if (value instanceof List) {
            StringBuilder joined = new StringBuilder();
            List<?> values = (List<?>) value;
            for (int index = 0; index < values.size(); index++) {
                if (joined.length() > 0) {
                    joined.append(' ');
                }
                joined.append(String.valueOf(values.get(index)));
            }
            target.put(name, joined.toString());
            return;
        }
        target.put(name, String.valueOf(value));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.length() == 0) {
            throw new OidcException("missing " + name);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String required(JsonNode node, String field, String url) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.asString().length() == 0) {
            throw new OidcException("oidc discovery from " + url + " lacks " + field);
        }
        return value.asString();
    }

    /** Rejection of an ID token that failed validation. */
    public static final class TokenRejectedException extends OidcException {
        private static final long serialVersionUID = 1L;

        public TokenRejectedException(String message) {
            super(message);
        }
    }

    /** Discovery fields used by the provider. */
    private static final class Discovery {
        private final String issuer;
        private final String authorizationEndpoint;
        private final String tokenEndpoint;
        private final String jwksUri;

        private Discovery(String issuer, String authorizationEndpoint, String tokenEndpoint, String jwksUri) {
            this.issuer = issuer;
            this.authorizationEndpoint = authorizationEndpoint;
            this.tokenEndpoint = tokenEndpoint;
            this.jwksUri = jwksUri;
        }

        private String issuer() {
            return issuer;
        }

        private String authorizationEndpoint() {
            return authorizationEndpoint;
        }

        private String tokenEndpoint() {
            return tokenEndpoint;
        }

        private String jwksUri() {
            return jwksUri;
        }
    }
}
