/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.github;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Hermetic GitHub stand-in: token exchange, user, and email endpoints. */
final class LoopbackGitHub implements AutoCloseable {
    private static final String DEFAULT_EMAILS =
            "[{\"email\":\"secondary@example.org\",\"primary\":false,\"verified\":true},"
            + "{\"email\":\"primary@example.org\",\"primary\":true,\"verified\":true},"
            + "{\"email\":\"unverified@example.org\",\"primary\":false,\"verified\":false}]";

    private final HttpServer server;
    private final AtomicInteger tokenHits = new AtomicInteger();
    private final AtomicInteger userHits = new AtomicInteger();
    private final AtomicInteger emailHits = new AtomicInteger();
    private final Map<String, String> tokenForm = new HashMap<String, String>();

    private volatile long userId = 424242L;
    private volatile String login = "octocat";
    private volatile String name = "The Octocat";
    private volatile String emailsJson = DEFAULT_EMAILS;
    private volatile String otpHeader;
    private volatile boolean twoFactorFlag;
    private volatile String tokenAccept;
    private volatile String userAccept;

    private LoopbackGitHub(HttpServer server) {
        this.server = server;
    }

    static LoopbackGitHub start() throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        final LoopbackGitHub stub = new LoopbackGitHub(server);
        server.createContext("/login/oauth/access_token", stub.handler("token"));
        server.createContext("/user/emails", stub.handler("emails"));
        server.createContext("/user", stub.handler("user"));
        server.start();
        return stub;
    }

    String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    int tokenHits() {
        return tokenHits.get();
    }

    int userHits() {
        return userHits.get();
    }

    int emailHits() {
        return emailHits.get();
    }

    String tokenAccept() {
        return tokenAccept;
    }

    String userAccept() {
        return userAccept;
    }

    String tokenFormValue(String key) {
        synchronized (tokenForm) {
            return tokenForm.get(key);
        }
    }

    void userId(long value) {
        this.userId = value;
    }

    void login(String value) {
        this.login = value;
    }

    void name(String value) {
        this.name = value;
    }

    void emails(String value) {
        this.emailsJson = value;
    }

    void otp(String value) {
        this.otpHeader = value;
    }

    void twoFactor(boolean value) {
        this.twoFactorFlag = value;
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
        if ("token".equals(path)) {
            tokenExchange(exchange);
        } else if ("user".equals(path)) {
            userExchange(exchange);
        } else {
            emailExchange(exchange);
        }
    }

    private void tokenExchange(HttpExchange exchange) throws IOException {
        tokenHits.incrementAndGet();
        tokenAccept = exchange.getRequestHeaders().getFirst("Accept");
        Map<String, String> form = parseQuery(body(exchange));
        synchronized (tokenForm) {
            tokenForm.clear();
            tokenForm.putAll(form);
        }
        String code = form.get("code");
        if (code == null || code.length() == 0 || "bad-code".equals(code)) {
            json(exchange, 200, "{\"error\":\"bad_verification_code\"}");
            return;
        }
        json(exchange, 200,
                "{\"access_token\":\"test-access-token\",\"token_type\":\"bearer\","
                + "\"scope\":\"read:user,user:email\"}");
    }

    private void userExchange(HttpExchange exchange) throws IOException {
        userHits.incrementAndGet();
        userAccept = exchange.getRequestHeaders().getFirst("Accept");
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            json(exchange, 401, "{\"message\":\"Bad credentials\"}");
            return;
        }
        StringBuilder response = new StringBuilder();
        response.append("{\"id\":").append(userId)
                .append(",\"login\":\"").append(login).append('"')
                .append(",\"name\":\"").append(name).append('"')
                .append(",\"email\":\"public@example.org\"");
        if (twoFactorFlag) {
            response.append(",\"two_factor_authentication\":true");
        }
        response.append('}');
        if (otpHeader != null) {
            exchange.getResponseHeaders().add("X-GitHub-OTP", otpHeader);
        }
        json(exchange, 200, response.toString());
    }

    private void emailExchange(HttpExchange exchange) throws IOException {
        emailHits.incrementAndGet();
        json(exchange, 200, emailsJson);
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
                values.put(URLDecoder.decode(pairs[index].substring(0, equal), StandardCharsets.UTF_8),
                        URLDecoder.decode(pairs[index].substring(equal + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
