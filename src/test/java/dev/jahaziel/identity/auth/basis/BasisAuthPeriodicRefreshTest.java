/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Opt-in pre-expiry refresh: window, scheduler lifecycle, and per-session serialization. */
final class BasisAuthPeriodicRefreshTest {
    private static final String SESSION = "periodic-session";
    private static final String OTHER_SESSION = "later-session";
    private static final String CLIENT = "example-web";
    private static final String SECRET = "client-secret-1234567890";
    private static final String RESOURCE = "urn:example:api";

    @TempDir Path temp;

    private RefreshStub stub;
    private PluginContext context;
    private BasisAuthPlugin plugin;
    private BasisStore store;

    @AfterEach
    void tearDown() {
        if (plugin != null) {
            plugin.shutdown();
        }
        if (stub != null) {
            stub.close();
        }
    }

    @Test
    void onlySessionsInsideTheWindowAreRefreshed() throws Exception {
        setUpPlugin("2");

        store.putTokenExpiresAt(SESSION, System.currentTimeMillis() + 1000L);
        store.putTokenExpiresAt(OTHER_SESSION, System.currentTimeMillis() + 600_000L);

        plugin.refreshDue();

        assertEquals("refresh-2", store.refreshToken(SESSION));
        assertEquals("refresh-1", store.refreshToken(OTHER_SESSION));
        assertTrue(
                store.sessionsNeedingRefresh(System.currentTimeMillis(), 60L * 60L * 1000L)
                        .contains(SESSION));
    }

    @Test
    void refreshIsOffWithoutAnExplicitWindow() throws Exception {
        setUpPlugin(null);

        store.putTokenExpiresAt(SESSION, System.currentTimeMillis() + 1000L);

        plugin.refreshDue();

        assertEquals("refresh-1", store.refreshToken(SESSION));
    }

    @Test
    void schedulerRunsAndShutdownStopsIt() throws Exception {
        setUpPlugin("3");
        store.putTokenExpiresAt(SESSION, System.currentTimeMillis() + 2500L);

        long deadline = System.currentTimeMillis() + 5000L;
        while (!"refresh-2".equals(store.refreshToken(SESSION))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }
        assertEquals(
                "refresh-2", store.refreshToken(SESSION), "scheduler did not refresh the session");

        plugin.shutdown();
        String stoppedAt = store.refreshToken(SESSION);
        store.putTokenExpiresAt(SESSION, System.currentTimeMillis() + 2500L);
        Thread.sleep(2000L);
        assertEquals(
                stoppedAt, store.refreshToken(SESSION), "scheduler kept running after shutdown");
    }

    @Test
    void concurrentCallerCannotRotateTheSameFamily() throws Exception {
        setUpPlugin(null);
        stub.gateNextRefresh();
        final AuthIdentity[] firstResult = new AuthIdentity[1];
        Thread first =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                firstResult[0] = plugin.refresh(context, SESSION);
                            }
                        },
                        "basis-refresh-test-first");
        first.start();
        assertTrue(stub.awaitGatedRefresh(), "first refresh never reached the token endpoint");

        assertNull(
                plugin.refresh(context, SESSION), "second caller must skip an in-flight refresh");

        stub.releaseRefresh();
        first.join(5000L);
        assertFalse(first.isAlive());
        assertNotNull(firstResult[0]);
        assertEquals("refresh-2", store.refreshToken(SESSION));
    }

    @Test
    void loginRecordsAccessTokenExpiryForTheScheduler() throws Exception {
        LoopbackIdp idp = LoopbackIdp.start(CLIENT, SECRET, RESOURCE);
        try {
            Map<String, String> values = settings(idp.issuer(), null);
            PluginContext loginContext = new PluginContext(temp.resolve("login"), values);
            BasisAuthPlugin loginPlugin = new BasisAuthPlugin();
            loginPlugin.initialize(loginContext);
            try {
                String state = "login-state";
                String url =
                        loginPlugin.startLogin(
                                loginContext,
                                state,
                                "login-nonce",
                                "https://app.example.org/callback");
                Map<String, String> parameters = new HashMap<String, String>();
                parameters.put("state", state);
                parameters.put("code", idp.authorize(url));
                loginPlugin.handleCallback(loginContext, parameters);

                BasisStore loginStore =
                        (BasisStore) loginContext.service(BasisAuthPlugin.SERVICE_STORE);
                assertFalse(
                        loginStore
                                .sessionsNeedingRefresh(System.currentTimeMillis(), 3600L * 1000L)
                                .isEmpty());
            } finally {
                loginPlugin.shutdown();
            }
        } finally {
            idp.close();
        }
    }

    private void setUpPlugin(String windowSeconds) throws Exception {
        stub = RefreshStub.start(CLIENT, SECRET, false);
        context = new PluginContext(temp, settings(stub.issuer(), windowSeconds));
        plugin = new BasisAuthPlugin();
        plugin.initialize(context);
        store = (BasisStore) context.service(BasisAuthPlugin.SERVICE_STORE);
        store.putSession(
                SESSION,
                new AuthIdentity(
                        "basis-auth",
                        "sub-1",
                        "user@example.org",
                        "User",
                        true,
                        true,
                        new HashMap<String, String>()));
        store.putRefreshToken(SESSION, "refresh-1");
        store.putSession(
                OTHER_SESSION,
                new AuthIdentity(
                        "basis-auth",
                        "sub-1",
                        "user@example.org",
                        "User",
                        true,
                        true,
                        new HashMap<String, String>()));
        store.putRefreshToken(OTHER_SESSION, "refresh-1");
    }

    private static Map<String, String> settings(String issuer, String windowSeconds) {
        Map<String, String> values = new HashMap<String, String>();
        values.put("BASIS_AUTH_ISSUER", issuer);
        values.put("BASIS_AUTH_CLIENT_ID", CLIENT);
        values.put("BASIS_AUTH_CLIENT_SECRET", SECRET);
        values.put("BASIS_AUTH_RESOURCE", RESOURCE);
        values.put("BASIS_AUTH_RESOURCE_SECRET", "resource-secret-1234567890");
        values.put("basis.offline-access", "true");
        if (windowSeconds != null) {
            values.put("basis.refresh-window-seconds", windowSeconds);
        }
        return values;
    }
}
