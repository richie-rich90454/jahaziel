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

/** Hermetic internal-users stub. Never records credentials in a way tests print. */
final class BasisInternalStub implements AutoCloseable {
    private final HttpServer server;

    private volatile boolean disabled = false;
    private volatile long barrier = 0L;
    private volatile int status = 200;
    private volatile boolean malformed = false;
    private volatile String authorization;
    private volatile String resource;
    private volatile String subject;

    private BasisInternalStub(HttpServer server) {
        this.server = server;
    }

    static BasisInternalStub start() throws Exception {
        HttpServer server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        final BasisInternalStub stub = new BasisInternalStub(server);
        server.createContext(
                "/internal/users/",
                new HttpHandler() {
                    @Override
                    public void handle(HttpExchange exchange) throws IOException {
                        stub.reply(exchange);
                    }
                });
        server.start();
        return stub;
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String authorization() {
        return authorization;
    }

    String resource() {
        return resource;
    }

    String subject() {
        return subject;
    }

    void disabled(boolean value) {
        disabled = value;
    }

    void barrier(long value) {
        barrier = value;
    }

    void status(int value) {
        status = value;
    }

    void malformed(boolean value) {
        malformed = value;
    }

    private void reply(HttpExchange exchange) throws IOException {
        authorization = exchange.getRequestHeaders().getFirst("Authorization");
        resource = exchange.getRequestHeaders().getFirst("X-Basis-Resource");
        String path = exchange.getRequestURI().getRawPath();
        subject = path.substring(path.lastIndexOf('/') + 1);
        if (status != 200) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        String body =
                malformed
                        ? "not-json"
                        : "{\"disabled\":" + disabled + ",\"tokensValidAfter\":" + barrier + "}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
