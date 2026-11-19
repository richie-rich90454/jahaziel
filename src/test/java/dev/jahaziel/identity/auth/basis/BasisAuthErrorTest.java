/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import dev.jahaziel.api.controller.ApiExceptionHandler;
import dev.jahaziel.api.dto.ApiError;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** OAuth error bodies become coded exceptions and coded API errors, without leaking the body. */
final class BasisAuthErrorTest {
    private HttpServer server;
    private BasisHttp http;
    private String url;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/error",
                new HttpHandler() {
                    @Override
                    public void handle(HttpExchange exchange) throws IOException {
                        String body =
                                "{\"error\":\"invalid_scope\",\"error_description\":\"secret-scope-value\"}";
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(400, bytes.length);
                        exchange.getResponseBody().write(bytes);
                        exchange.close();
                    }
                });
        server.createContext(
                "/ok",
                new HttpHandler() {
                    @Override
                    public void handle(HttpExchange exchange) throws IOException {
                        byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                        exchange.close();
                    }
                });
        server.start();
        http = new BasisHttp(HttpClient.newHttpClient());
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void httpParsesOAuthErrorIntoACodedExceptionWithoutKeepingTheBody() {
        BasisOAuthException failure =
                assertThrows(
                        BasisOAuthException.class,
                        new ThrowingGet(url + "/error"));

        assertEquals(14401, failure.code());
        assertEquals("invalid_scope", failure.error());
        assertTrue(failure.recovery().contains("scope"));
        assertFalse(failure.getMessage().contains("secret-scope-value"));
    }

    @Test
    void unmappedErrorsFailClosedWithCodeZero() {
        BasisOAuthException failure =
                BasisOAuthException.from(400, "{\"error\":\"something_new\"}");

        assertEquals(0, failure.code());
        assertNotNull(failure.recovery());
    }

    @Test
    void apiExceptionHandlerCarriesTheCodeAndRecoveryGuidance() {
        ApiError error =
                new ApiExceptionHandler()
                        .basisOAuthFailed(
                                BasisOAuthException.from(
                                        400, "{\"error\":\"unsupported_response_type\"}"));

        assertEquals("14429", error.code());
        assertEquals("14429", error.details().get("code"));
        assertTrue(error.details().get("recovery").contains("response_type=code"));
    }

    @Test
    void successfulResponsesStillReturnTheirBody() {
        assertEquals("{\"ok\":true}", http.get(url + "/ok", Duration.ofSeconds(5)));
    }

    private final class ThrowingGet implements org.junit.jupiter.api.function.Executable {
        private final String target;

        private ThrowingGet(String target) {
            this.target = target;
        }

        @Override
        public void execute() {
            http.get(target, Duration.ofSeconds(5));
        }
    }

}
