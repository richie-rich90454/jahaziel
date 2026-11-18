/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Hermetic basis-auth stand-in: discovery, authorize, token, JWKS, and webhook proof. */
final class LoopbackIdp implements AutoCloseable {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;
    private final RSAKey signingKey;
    private final String clientId;
    private final String clientSecret;
    private final String resource;
    private final Map<String, PendingAuth> codes = new ConcurrentHashMap<String, PendingAuth>();
    private final AtomicInteger discoveryHits = new AtomicInteger();
    private final AtomicInteger tokenHits = new AtomicInteger();
    private final AtomicInteger jwksHits = new AtomicInteger();

    private volatile String subject = "basis-sub-1";
    private volatile String email = "user@example.org";
    private volatile String name = "Basis User";
    private volatile java.util.List<String> amr = Arrays.asList("pwd", "mfa");
    private volatile String acr = "urn:example:assurance:mfa";
    private volatile boolean issueRefresh = true;
    private volatile boolean basicRequired = true;
    private volatile String refreshToken = "plain-refresh-token-value";
    private volatile JsonNode proofBody;
    private volatile String proofAuthorization;
    private volatile String proofResource;

    private LoopbackIdp(HttpServer server, RSAKey signingKey, String clientId, String clientSecret, String resource) {
        this.server = server;
        this.signingKey = signingKey;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.resource = resource;
    }

    static LoopbackIdp start(String clientId, String clientSecret, String resource) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        RSAKey key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        final LoopbackIdp idp = new LoopbackIdp(server, key, clientId, clientSecret, resource);
        server.createContext("/.well-known/openid-configuration", idp.handler("discovery"));
        server.createContext("/authorize", idp.handler("authorize"));
        server.createContext("/oauth/token", idp.handler("token"));
        server.createContext("/oauth/jwks", idp.handler("jwks"));
        server.createContext("/internal/backchannel/validate", idp.handler("proof"));
        server.start();
        return idp;
    }

    String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    int jwksHits() {
        return jwksHits.get();
    }

    int tokenHits() {
        return tokenHits.get();
    }

    JsonNode proofBody() {
        return proofBody;
    }

    String proofAuthorization() {
        return proofAuthorization;
    }

    String proofResource() {
        return proofResource;
    }

    String refreshToken() {
        return refreshToken;
    }

    void issueRefresh(boolean value) {
        this.issueRefresh = value;
    }

    void basicRequired(boolean value) {
        this.basicRequired = value;
    }

    void assurance(java.util.List<String> amr, String acr) {
        this.amr = amr;
        this.acr = acr;
    }

    /** Performs the browser redirect against the stub and returns the issued code. */
    String authorize(String authorizeUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(authorizeUrl))
                .GET()
                .build();
        HttpClient noRedirect = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpResponse<Void> response = noRedirect
                .send(request, HttpResponse.BodyHandlers.discarding());
        String location = response.headers().firstValue("Location").orElse("");
        return valueOf(location, "code");
    }

    /** Signs an arbitrary token with the given type header and claims. */
    String sign(String type, Map<String, Object> claims) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            builder.claim(entry.getKey(), entry.getValue());
        }
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType(type))
                .keyID(signingKey.getKeyID())
                .build();
        SignedJWT jwt = new SignedJWT(header, builder.build());
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (Exception failure) {
            throw new IllegalStateException("cannot sign token", failure);
        }
        return jwt.serialize();
    }

    private HttpHandler handler(final String path) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                dispatch(path, exchange);
            }
        };
    }

    private void dispatch(String path, HttpExchange exchange) throws IOException {
        if ("discovery".equals(path)) {
            discoveryHits.incrementAndGet();
            json(exchange, 200, "{\"issuer\":\"" + issuer()
                    + "\",\"authorization_endpoint\":\"" + issuer() + "/authorize"
                    + "\",\"token_endpoint\":\"" + issuer() + "/oauth/token"
                    + "\",\"jwks_uri\":\"" + issuer() + "/oauth/jwks\"}");
        } else if ("authorize".equals(path)) {
            authorizeExchange(exchange);
        } else if ("token".equals(path)) {
            tokenExchange(exchange);
        } else if ("jwks".equals(path)) {
            jwksHits.incrementAndGet();
            json(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString());
        } else {
            proofExchange(exchange);
        }
    }

    private void authorizeExchange(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String state = query.get("state");
        String redirect = query.get("redirect_uri");
        if (state == null || redirect == null) {
            json(exchange, 400, "{\"error\":\"invalid_request\"}");
            return;
        }
        String code = UUID.randomUUID().toString();
        codes.put(code, new PendingAuth(query.get("nonce"), query.get("code_challenge")));
        String location = redirect + (redirect.contains("?") ? '&' : '?')
                + "code=" + code + "&state=" + valueEncode(state);
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void tokenExchange(HttpExchange exchange) throws IOException {
        tokenHits.incrementAndGet();
        if (basicRequired && !basicAuthorized(exchange)) {
            json(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        Map<String, String> form = parseQuery(body(exchange));
        PendingAuth pending = codes.remove(form.get("code"));
        if (pending == null || pending.challenge == null
                || !Pkce.challenge(form.get("code_verifier")).equals(pending.challenge)) {
            json(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        String jti = "access-jti-" + tokenHits.get();
        String access = accessToken(jti);
        String id = idToken(pending.nonce, access);
        StringBuilder response = new StringBuilder();
        response.append("{\"id_token\":\"").append(id)
                .append("\",\"access_token\":\"").append(access)
                .append("\",\"token_type\":\"Bearer\"");
        if (issueRefresh) {
            response.append(",\"refresh_token\":\"").append(refreshToken).append('"');
        }
        response.append(",\"expires_in\":3600}");
        json(exchange, 200, response.toString());
    }

    private void proofExchange(HttpExchange exchange) throws IOException {
        proofAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
        proofResource = exchange.getRequestHeaders().getFirst("X-Basis-Resource");
        try {
            proofBody = mapper.readTree(body(exchange));
        } catch (Exception failure) {
            json(exchange, 400, "{\"error\":\"invalid_request\"}");
            return;
        }
        json(exchange, 200, "{\"status\":\"successful\"}");
    }

    String accessToken(String jti) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer())
                .subject(subject)
                .audience(resource)
                .jwtID(jti)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                .claim("scope", "openid profile email")
                .claim("permissions", Arrays.asList("projects.member"))
                .claim("gty", "authorization_code")
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("at+jwt"))
                .keyID(signingKey.getKeyID())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (Exception failure) {
            throw new IllegalStateException("cannot sign access token", failure);
        }
        return jwt.serialize();
    }

    private String idToken(String nonce, String access) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer())
                .subject(subject)
                .audience(clientId)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 600_000L))
                .claim("nonce", nonce)
                .claim("email", email)
                .claim("name", name)
                .claim("email_verified", Boolean.TRUE)
                .claim("amr", amr)
                .claim("acr", acr)
                .claim("at_hash", Pkce.atHash(access))
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKey.getKeyID())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (Exception failure) {
            throw new IllegalStateException("cannot sign ID token", failure);
        }
        return jwt.serialize();
    }

    private boolean basicAuthorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Basic ")) {
            return false;
        }
        String decoded = new String(
                Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
        return (clientId + ":" + clientSecret).equals(decoded);
    }

    private static String body(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> values = new HashMap<String, String>();
        if (raw == null || raw.length() == 0) {
            return values;
        }
        String[] pairs = raw.split("&");
        for (int index = 0; index < pairs.length; index++) {
            int equal = pairs[index].indexOf('=');
            if (equal > 0) {
                values.put(valueDecode(pairs[index].substring(0, equal)),
                        valueDecode(pairs[index].substring(equal + 1)));
            }
        }
        return values;
    }

    private static String valueEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String valueDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String valueOf(String url, String name) {
        Map<String, String> values = parseQuery(url.contains("?") ? url.substring(url.indexOf('?') + 1) : "");
        return values.get(name);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static final class PendingAuth {
        private final String nonce;
        private final String challenge;

        private PendingAuth(String nonce, String challenge) {
            this.nonce = nonce;
            this.challenge = challenge;
        }
    }
}
