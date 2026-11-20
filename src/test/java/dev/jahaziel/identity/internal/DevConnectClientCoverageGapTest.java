/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import dev.jahaziel.identity.api.DevConnectException;
import dev.jahaziel.identity.internal.devconnect.DevConnectClient;
import dev.jahaziel.identity.internal.devconnect.DevConnectProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class DevConnectClientCoverageGapTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private DevConnectProperties props(String base) {
        DevConnectProperties p = new DevConnectProperties();
        p.setAuthIssuer(base);
        p.setRedirectUri("https://app/cb");
        p.setClientId("jahaziel");
        p.setClientSecret("s3cret");
        p.setResource("urn:basis:api:jahaziel");
        return p;
    }

    private String discoveryJson(String base) {
        return "{\"issuer\":\""
                + base
                + "\",\"authorization_endpoint\":\""
                + base
                + "/authorize\",\"token_endpoint\":\""
                + base
                + "/token\",\"userinfo_endpoint\":\""
                + base
                + "/userinfo\",\"jwks_uri\":\""
                + base
                + "/jwks\"}";
    }

    private HttpHandler discoveryHandler(final String base) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                byte[] b = discoveryJson(base).getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            }
        };
    }

    private HttpHandler bodyHandler(final AtomicReference<String> body) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                byte[] b = body.get().getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            }
        };
    }

    private HttpHandler errorHandler() {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                ex.sendResponseHeaders(500, 0);
                ex.close();
            }
        };
    }

    private DevConnectClient client(String base) {
        return new DevConnectClient(
                RestClient.builder().build(), new RestTemplate(), props(base), Clock.systemUTC());
    }

    private static Executable verify(
            final DevConnectClient c, final String token, final String nonce) {
        return new Executable() {
            @Override
            public void execute() {
                c.verifyIdToken(token, nonce);
            }
        };
    }

    private static Executable userinfo(final DevConnectClient c, final String token) {
        return new Executable() {
            @Override
            public void execute() {
                c.userinfo(token);
            }
        };
    }

    private static Executable exchange(final DevConnectClient c) {
        return new Executable() {
            @Override
            public void execute() {
                c.exchange("code", "ver", "https://app/cb");
            }
        };
    }

    @Test
    void userinfoHttpFailureThrows() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;
        server.createContext("/.well-known/openid-configuration", discoveryHandler(base));
        server.createContext("/userinfo", errorHandler());
        server.start();
        DevConnectClient c = client(base);
        DevConnectException failure = assertThrows(DevConnectException.class, userinfo(c, "token"));
        assertTrue(failure.getMessage().contains("UserInfo failed"));
    }

    @Test
    void discoveryUnreachableRetriesThenFails() throws Exception {
        int closed;
        try (ServerSocket probe = new ServerSocket(0)) {
            closed = probe.getLocalPort();
        }
        String base = "http://127.0.0.1:" + closed;
        DevConnectClient c = client(base);
        DevConnectException failure =
                assertThrows(
                        DevConnectException.class,
                        new Executable() {
                            @Override
                            public void execute() {
                                c.discovery();
                            }
                        });
        assertTrue(failure.getMessage().contains("discovery failed"));
    }

    @Test
    void exchangeMissingIdTokenThrows() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;
        AtomicReference<String> tokenBody = new AtomicReference<>("{\"access_token\":\"a\"}");
        server.createContext("/.well-known/openid-configuration", discoveryHandler(base));
        server.createContext("/token", bodyHandler(tokenBody));
        server.start();
        DevConnectClient c = client(base);
        assertThrows(DevConnectException.class, exchange(c));
        tokenBody.set("{\"id_token\":null,\"access_token\":\"a\"}");
        assertThrows(DevConnectException.class, exchange(c));
    }

    @Test
    void userinfoMissingSubThrows() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;
        AtomicReference<String> userBody = new AtomicReference<>("{\"email\":\"e@x.y\"}");
        server.createContext("/.well-known/openid-configuration", discoveryHandler(base));
        server.createContext("/userinfo", bodyHandler(userBody));
        server.start();
        DevConnectClient c = client(base);
        assertThrows(DevConnectException.class, userinfo(c, "token"));
        userBody.set("{\"sub\":null,\"email\":\"e@x.y\"}");
        assertThrows(DevConnectException.class, userinfo(c, "token"));
    }

    @Test
    void readNullBodyThrowsEmpty() throws Exception {
        Method read = DevConnectClient.class.getDeclaredMethod("read", String.class, String.class);
        read.setAccessible(true);
        DevConnectClient client = client("http://x");
        try {
            read.invoke(client, null, "http://x/token");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof DevConnectException);
            assertTrue(expected.getCause().getMessage().contains("was empty"));
        }
    }

    @Test
    void causeHandlesSelfCausedThrowable() throws Exception {
        Method cause = DevConnectClient.class.getDeclaredMethod("cause", Throwable.class);
        cause.setAccessible(true);
        Throwable self = mock(Throwable.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(self.getCause()).thenReturn(self);
        when(self.getMessage()).thenReturn("boom");
        String label = (String) cause.invoke(null, self);
        assertTrue(label.contains("boom"));
    }

    @Test
    void decoderBuildsWhenAbsentAndRebuildsOnJwksChange() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;
        server.createContext("/.well-known/openid-configuration", discoveryHandler(base));
        server.start();
        DevConnectClient c = client(base);
        assertThrows(DevConnectException.class, verify(c, "a.b.c", null));
        Field decoderField = DevConnectClient.class.getDeclaredField("decoder");
        decoderField.setAccessible(true);
        assertTrue(decoderField.get(c) != null);
        JwtDecoder stale = mock(JwtDecoder.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("nonce")).thenReturn("n");
        when(stale.decode("good")).thenReturn(jwt);
        decoderField.set(c, stale);
        Field uriField = DevConnectClient.class.getDeclaredField("decoderJwksUri");
        uriField.setAccessible(true);
        uriField.set(c, base + "/jwks");
        assertEquals(jwt, c.verifyIdToken("good", "n"));
        assertTrue(decoderField.get(c) == stale);
        uriField.set(c, base + "/old-jwks");
        assertThrows(DevConnectException.class, verify(c, "good", "n"));
        assertTrue(decoderField.get(c) != stale);
    }

    @Test
    @SuppressWarnings("unchecked")
    void audienceValidatorHandlesNullWrongAndRight() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;
        server.createContext("/.well-known/openid-configuration", discoveryHandler(base));
        server.start();
        DevConnectClient c = client(base);
        assertThrows(DevConnectException.class, verify(c, "a.b.c", null));
        Field decoderField = DevConnectClient.class.getDeclaredField("decoder");
        decoderField.setAccessible(true);
        NimbusJwtDecoder built = (NimbusJwtDecoder) decoderField.get(c);
        Field validatorField = NimbusJwtDecoder.class.getDeclaredField("jwtValidator");
        validatorField.setAccessible(true);
        OAuth2TokenValidator<Jwt> validator = (OAuth2TokenValidator<Jwt>) validatorField.get(built);
        Jwt missing = mock(Jwt.class);
        when(missing.getAudience()).thenReturn(null);
        assertTrue(validator.validate(missing).hasErrors());
        Jwt wrong = mock(Jwt.class);
        when(wrong.getAudience()).thenReturn(List.of("other"));
        assertTrue(validator.validate(wrong).hasErrors());
        Jwt right =
                new Jwt(
                        "token",
                        Instant.now(),
                        Instant.now().plusSeconds(300),
                        Map.of("alg", "none"),
                        Map.of("aud", List.of("jahaziel"), "iss", base));
        validator.validate(right);
    }

    @Test
    void concurrentDiscoverySkipsSecondFetch() throws Exception {
        AtomicReference<String> body = new AtomicReference<>("");
        final AtomicInteger hits = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;
        body.set(discoveryJson(base));
        server.createContext(
                "/.well-known/openid-configuration",
                new HttpHandler() {
                    @Override
                    public void handle(HttpExchange ex) throws IOException {
                        hits.incrementAndGet();
                        try {
                            Thread.sleep(800);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        byte[] b = body.get().getBytes(StandardCharsets.UTF_8);
                        ex.sendResponseHeaders(200, b.length);
                        ex.getResponseBody().write(b);
                        ex.close();
                    }
                });
        server.start();
        Clock fixed = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        final DevConnectClient c =
                new DevConnectClient(
                        RestClient.builder().build(), new RestTemplate(), props(base), fixed);
        c.discovery();
        assertEquals(1, hits.get());
        Field clockField = DevConnectClient.class.getDeclaredField("clock");
        clockField.setAccessible(true);
        clockField.set(c, Clock.offset(fixed, java.time.Duration.ofMinutes(6)));
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first =
                    pool.submit(
                            new Runnable() {
                                @Override
                                public void run() {
                                    ready.countDown();
                                    try {
                                        go.await();
                                        c.discovery();
                                    } catch (Exception failure) {
                                        throw new AssertionError(failure);
                                    }
                                }
                            });
            Future<?> second =
                    pool.submit(
                            new Runnable() {
                                @Override
                                public void run() {
                                    ready.countDown();
                                    try {
                                        go.await();
                                        c.discovery();
                                    } catch (Exception failure) {
                                        throw new AssertionError(failure);
                                    }
                                }
                            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, hits.get());
    }
}
