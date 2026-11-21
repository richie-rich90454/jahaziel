/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.text.ParseException;
import java.time.Duration;
import java.util.Locale;

/** Discovery and JWKS, cached for five minutes and shared by login and backchannel checks. */
public final class BasisMetadata {
    private static final long TTL_MILLIS = 5L * 60L * 1000L;
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

    private final BasisConfig config;
    private final BasisHttp http;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile Discovery discovery;
    private volatile long discoveryAt;
    private volatile JWKSet keys;
    private volatile long keysAt;

    public BasisMetadata(BasisConfig config, BasisHttp http) {
        this.config = config;
        this.http = http;
    }

    public Discovery discovery() {
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

    public JWSVerifier verifierFor(String keyId) {
        JWKSet set = jwks(false);
        JWK key = find(set, keyId);
        if (key == null) {
            set = jwks(true);
            key = find(set, keyId);
        }
        if (!(key instanceof RSAKey)) {
            throw new BasisException("basis-auth JWKS has no usable signing key");
        }
        try {
            return new RSASSAVerifier(((RSAKey) key).toRSAPublicKey());
        } catch (JOSEException failure) {
            throw new BasisException("basis-auth signing key is not RSA", failure);
        }
    }

    private Discovery loadDiscovery() {
        String issuer = config.issuer();
        if (!endpointAllowed(issuer, issuer)) {
            throw new BasisException("basis-auth issuer is not an allowed http(s) URL");
        }
        String url = issuer + "/.well-known/openid-configuration";
        String body = http.get(url, FETCH_TIMEOUT);
        JsonNode node;
        try {
            node = mapper.readTree(body);
        } catch (JacksonException failure) {
            throw new BasisException("basis-auth discovery is not JSON", failure);
        }
        Discovery loaded =
                new Discovery(
                        required(node, "issuer", url),
                        required(node, "authorization_endpoint", url),
                        required(node, "token_endpoint", url),
                        required(node, "jwks_uri", url),
                        optional(node, "userinfo_endpoint", issuer + "/oauth/userinfo"));
        if (!issuer.equals(loaded.issuer())) {
            throw new BasisException("basis-auth discovery issuer does not match configuration");
        }
        requireSameOrigin(issuer, loaded.issuer(), "issuer");
        requireSameOrigin(issuer, loaded.authorizationEndpoint(), "authorization_endpoint");
        requireSameOrigin(issuer, loaded.tokenEndpoint(), "token_endpoint");
        requireSameOrigin(issuer, loaded.jwksUri(), "jwks_uri");
        requireSameOrigin(issuer, loaded.userinfoEndpoint(), "userinfo_endpoint");
        return loaded;
    }

    private static void requireSameOrigin(String issuer, String endpoint, String field) {
        if (!endpointAllowed(issuer, endpoint)) {
            throw new BasisException(
                    "basis-auth discovery " + field + " is not on the configured issuer host");
        }
    }

    /**
     * True only for an HTTPS issuer, or an HTTP loopback issuer used in development, whose endpoint
     * shares its host and scheme. An explicit port is allowed; redirects to other hosts are not.
     */
    public static boolean endpointAllowed(String issuer, String endpoint) {
        try {
            URI base = URI.create(issuer);
            URI target = URI.create(endpoint);
            String scheme = lower(base.getScheme());
            String baseHost = host(base.getHost());
            boolean allowed =
                    "https".equals(scheme) || ("http".equals(scheme) && loopback(baseHost));
            return allowed
                    && baseHost.length() > 0
                    && scheme.equals(lower(target.getScheme()))
                    && baseHost.equals(host(target.getHost()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean loopback(String host) {
        return "localhost".equals(host)
                || "::1".equals(host)
                || "0:0:0:0:0:0:0:1".equals(host)
                || host.startsWith("127.");
    }

    private static String host(String value) {
        String result = lower(value);
        if (result.startsWith("[") && result.endsWith("]")) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private JWKSet jwks(boolean force) {
        JWKSet found = keys;
        if (!force && found != null && fresh(keysAt)) {
            return found;
        }
        synchronized (this) {
            if (force || keys == null || !fresh(keysAt)) {
                String url = discovery().jwksUri();
                String body = http.get(url, FETCH_TIMEOUT);
                try {
                    keys = JWKSet.parse(body);
                } catch (ParseException failure) {
                    throw new BasisException("basis-auth JWKS is not valid JSON", failure);
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
            for (JWK candidate : set.getKeys()) {
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

    private static String optional(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.asString().length() == 0
                ? fallback
                : value.asString();
    }

    private static String required(JsonNode node, String field, String url) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.asString().length() == 0) {
            throw new BasisException("basis-auth discovery from " + url + " lacks " + field);
        }
        return value.asString();
    }

    /** The discovery fields the provider uses. */
    public static final class Discovery {
        private final String issuer;
        private final String authorizationEndpoint;
        private final String tokenEndpoint;
        private final String jwksUri;
        private final String userinfoEndpoint;

        private Discovery(
                String issuer,
                String authorizationEndpoint,
                String tokenEndpoint,
                String jwksUri,
                String userinfoEndpoint) {
            this.issuer = issuer;
            this.authorizationEndpoint = authorizationEndpoint;
            this.tokenEndpoint = tokenEndpoint;
            this.jwksUri = jwksUri;
            this.userinfoEndpoint = userinfoEndpoint;
        }

        public String issuer() {
            return issuer;
        }

        public String authorizationEndpoint() {
            return authorizationEndpoint;
        }

        public String tokenEndpoint() {
            return tokenEndpoint;
        }

        public String jwksUri() {
            return jwksUri;
        }

        public String userinfoEndpoint() {
            return userinfoEndpoint;
        }
    }
}
