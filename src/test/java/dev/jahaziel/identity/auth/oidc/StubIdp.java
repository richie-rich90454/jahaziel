/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.oidc;

import com.nimbusds.jose.JOSEException;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** In-process discovery, JWKS and token endpoints. Tests never touch the network. */
final class StubIdp {
    static final String ACCESS_TOKEN = "stub-access-token";
    static final String CLIENT_ID = "test-client";

    private final HttpServer server;
    private final RSAKey signingKey;
    private final String issuer;
    private volatile String nextClaims;
    private volatile String lastAuthorization;
    private volatile Map<String, String> lastForm = new LinkedHashMap<String, String>();

    StubIdp() {
        try {
            this.signingKey = new RSAKeyGenerator(2048).keyID("stub-key").generate();
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.issuer = "http://127.0.0.1:" + server.getAddress().getPort();
            server.createContext(
                    "/.well-known/openid-configuration",
                    new HttpHandler() {
                        @Override
                        public void handle(HttpExchange exchange) throws IOException {
                            String json =
                                    "{\"issuer\":\""
                                            + issuer
                                            + "\","
                                            + "\"authorization_endpoint\":\""
                                            + issuer
                                            + "/authorize\","
                                            + "\"token_endpoint\":\""
                                            + issuer
                                            + "/token\","
                                            + "\"jwks_uri\":\""
                                            + issuer
                                            + "/jwks\"}";
                            respond(exchange, json);
                        }
                    });
            server.createContext(
                    "/jwks",
                    new HttpHandler() {
                        @Override
                        public void handle(HttpExchange exchange) throws IOException {
                            respond(exchange, new JWKSet(signingKey.toPublicJWK()).toString());
                        }
                    });
            server.createContext(
                    "/token",
                    new HttpHandler() {
                        @Override
                        public void handle(HttpExchange exchange) throws IOException {
                            lastAuthorization =
                                    exchange.getRequestHeaders().getFirst("Authorization");
                            String body =
                                    new String(
                                            readAll(exchange.getRequestBody()),
                                            StandardCharsets.UTF_8);
                            lastForm = parseForm(body);
                            String json =
                                    "{\"id_token\":\""
                                            + idToken(nextClaims)
                                            + "\","
                                            + "\"access_token\":\""
                                            + ACCESS_TOKEN
                                            + "\","
                                            + "\"token_type\":\"Bearer\",\"refresh_token\":\"stub-refresh\"}";
                            respond(exchange, json);
                        }
                    });
            server.start();
        } catch (JOSEException failure) {
            throw new IllegalStateException(failure);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    String issuer() {
        return issuer;
    }

    String authorizationEndpoint() {
        return issuer + "/authorize";
    }

    void setNextClaims(String claimsJson) {
        this.nextClaims = claimsJson;
    }

    String lastAuthorization() {
        return lastAuthorization;
    }

    Map<String, String> lastForm() {
        return lastForm;
    }

    void stop() {
        server.stop(0);
    }

    String idToken(String claimsJson) {
        try {
            JWTClaimsSet claims = JWTClaimsSet.parse(claimsJson);
            SignedJWT jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("stub-key").build(),
                            claims);
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (ParseException failure) {
            throw new IllegalStateException(failure);
        } catch (JOSEException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static String atHash(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.US_ASCII));
            byte[] half = new byte[16];
            System.arraycopy(digest, 0, half, 0, half.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(half);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String challenge(String verifier) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream stream = exchange.getResponseBody();
        stream.write(bytes);
        stream.close();
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        String[] pairs = body.split("&");
        for (int index = 0; index < pairs.length; index++) {
            int equals = pairs[index].indexOf('=');
            if (equals < 0) {
                values.put(decode(pairs[index]), "");
            } else {
                values.put(
                        decode(pairs[index].substring(0, equals)),
                        decode(pairs[index].substring(equals + 1)));
            }
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
