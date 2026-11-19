/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Small loopback token endpoint that rotates refresh tokens like basis-auth. */
final class RefreshStub implements AutoCloseable {
    private final HttpServer server;
    private final String clientId;
    private final String clientSecret;
    private final boolean publicClient;

    private volatile String current = "refresh-1";
    private volatile int issued = 1;
    private volatile boolean failRefresh;
    private volatile String lastGrant;
    private volatile String lastAuthorization;
    private volatile String lastRefresh;
    private volatile String lastClientId;

    private RefreshStub(
            HttpServer server, String clientId, String clientSecret, boolean publicClient) {
        this.server = server;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.publicClient = publicClient;
    }

    static RefreshStub start(String clientId, String clientSecret, boolean publicClient)
            throws Exception {
        HttpServer server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        RefreshStub stub = new RefreshStub(server, clientId, clientSecret, publicClient);
        server.createContext("/.well-known/openid-configuration", stub.handler("discovery"));
        server.createContext("/oauth/token", stub.handler("token"));
        server.start();
        return stub;
    }

    String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String current() {
        return current;
    }

    String lastGrant() {
        return lastGrant;
    }

    String lastAuthorization() {
        return lastAuthorization;
    }

    String lastRefresh() {
        return lastRefresh;
    }

    String lastClientId() {
        return lastClientId;
    }

    void failRefresh(boolean value) {
        this.failRefresh = value;
    }

    private HttpHandler handler(final String path) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("discovery".equals(path)) {
                    discovery(exchange);
                } else {
                    token(exchange);
                }
            }
        };
    }

    private void discovery(HttpExchange exchange) throws IOException {
        String base = issuer();
        String body =
                "{\"issuer\":\""
                        + base
                        + "\",\"authorization_endpoint\":\""
                        + base
                        + "/authorize\",\"token_endpoint\":\""
                        + base
                        + "/oauth/token\",\"jwks_uri\":\""
                        + base
                        + "/oauth/jwks\"}";
        json(exchange, 200, body);
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = parse(body(exchange));
        lastGrant = form.get("grant_type");
        lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
        lastRefresh = form.get("refresh_token");
        lastClientId = form.get("client_id");
        if (!authorized(exchange, form)) {
            json(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        if (!"refresh_token".equals(lastGrant) || failRefresh || !current.equals(lastRefresh)) {
            json(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        issued++;
        current = "refresh-" + issued;
        String body =
                "{\"access_token\":\"new-access-"
                        + issued
                        + "\",\"id_token\":\"new-id-"
                        + issued
                        + "\",\"refresh_token\":\""
                        + current
                        + "\",\"expires_in\":1800}";
        json(exchange, 200, body);
    }

    private boolean authorized(HttpExchange exchange, Map<String, String> form) {
        if (publicClient) {
            return clientId.equals(form.get("client_id"));
        }
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Basic ")) {
            return false;
        }
        String decoded =
                new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
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

    private static Map<String, String> parse(String raw) {
        Map<String, String> values = new HashMap<String, String>();
        if (raw == null || raw.length() == 0) {
            return values;
        }
        String[] pairs = raw.split("&");
        for (int index = 0; index < pairs.length; index++) {
            int equal = pairs[index].indexOf('=');
            if (equal > 0) {
                values.put(
                        java.net.URLDecoder.decode(
                                pairs[index].substring(0, equal), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(
                                pairs[index].substring(equal + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
