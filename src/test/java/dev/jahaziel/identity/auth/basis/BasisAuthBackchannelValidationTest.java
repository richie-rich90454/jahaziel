/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Webhook input validation, 202-first behavior, and strict logout event structure. */
final class BasisAuthBackchannelValidationTest {
    private static final String RESOURCE = "urn:example:api";
    private static final String CLIENT = "example-web";
    private static final String SECRET = "client-secret-1234567890";
    private static final String RESOURCE_SECRET = "resource-secret-1234567890";

    @TempDir Path temp;

    private LoopbackIdp idp;
    private PluginContext context;
    private BasisAuthPlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        idp = LoopbackIdp.start(CLIENT, SECRET, RESOURCE);
        context = new PluginContext(temp, settings(idp.issuer(), idp.issuer()));
        plugin = new BasisAuthPlugin();
        plugin.initialize(context);
    }

    @AfterEach
    void tearDown() {
        idp.close();
    }

    @Test
    void malformedNonceOrChallengeIsRejectedWithoutDelivery() {
        assertEquals(400, webhook("short", value('n')).status());
        assertEquals(400, webhook(value('n'), "bad!challenge-with-wrong-length!!!!!!!!!!").status());
        assertNull(idp.proofBody());
    }

    @Test
    void logoutEventMustBeAnEmptyObject() {
        Map<String, Object> claims = baseClaims();
        claims.put("sub", "sub-1");
        Map<String, Object> event = new HashMap<String, Object>();
        event.put("unexpected", "value");
        claims.put(TokenVerifier.LOGOUT_EVENT, event);
        Map<String, String> form = new HashMap<String, String>();
        form.put("logout_token", idp.sign("logout+jwt", claims));

        assertThrows(BasisException.class, new ThrowingLogout(form));
    }

    @Test
    void webhookReturns202BeforeTheProofIsDelivered() throws Exception {
        HttpServer slow = slowProofServer();
        try {
            PluginContext slowContext = new PluginContext(temp.resolve("slow"), settings(idp.issuer(), base(slow)));
            BasisAuthPlugin slowPlugin = new BasisAuthPlugin();
            slowPlugin.initialize(slowContext);

            long started = System.currentTimeMillis();
            HttpResult result = slowPlugin.backchannel().handle(webhookForm());
            long elapsed = System.currentTimeMillis() - started;

            assertEquals(202, result.status());
            assertTrue(elapsed < 200L, "webhook blocked on proof delivery: " + elapsed + "ms");
        } finally {
            slow.stop(0);
        }
    }

    private HttpResult webhook(String nonce, String challenge) {
        Map<String, String> form = webhookForm();
        form.put("nonce", nonce);
        form.put("challenge", challenge);
        return plugin.backchannel().handle(form);
    }

    private static Map<String, String> webhookForm() {
        Map<String, String> form = new HashMap<String, String>();
        form.put("action", "test");
        form.put("nonce", value('n'));
        form.put("challenge", value('c'));
        return form;
    }

    private static String value(char value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < 43; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private Map<String, Object> baseClaims() {
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put("iss", idp.issuer());
        claims.put("aud", RESOURCE);
        claims.put("exp", Long.valueOf(System.currentTimeMillis() / 1000L + 120L));
        claims.put("iat", Long.valueOf(System.currentTimeMillis() / 1000L));
        return claims;
    }

    private static HttpServer slowProofServer() throws Exception {
        HttpServer server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/internal/backchannel/validate",
                new HttpHandler() {
                    @Override
                    public void handle(HttpExchange exchange) throws IOException {
                        try {
                            Thread.sleep(400L);
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                        }
                        byte[] bytes = "{\"status\":\"successful\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                        exchange.close();
                    }
                });
        server.start();
        return server;
    }

    private static String base(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static Map<String, String> settings(String issuer, String internalUrl) {
        Map<String, String> values = new HashMap<String, String>();
        values.put("basis.auth-issuer", issuer);
        values.put("basis.client-id", CLIENT);
        values.put("basis.client-secret", SECRET);
        values.put("basis.resource", RESOURCE);
        values.put("basis.resource-secret", RESOURCE_SECRET);
        values.put("basis.internal-url", internalUrl);
        return values;
    }

    private final class ThrowingLogout implements org.junit.jupiter.api.function.Executable {
        private final Map<String, String> form;

        private ThrowingLogout(Map<String, String> form) {
            this.form = form;
        }

        @Override
        public void execute() {
            plugin.backchannel().handle(form);
        }
    }
}
