/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.BasisAuthPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Refresh rotation, single-use enforcement, invalid_grant reset, and encryption at rest. */
final class BasisAuthRefreshTest {
    private static final String SESSION = "refresh-session";
    private static final String CLIENT = "example-web";
    private static final String SECRET = "client-secret-1234567890";
    private static final String RESOURCE = "urn:example:api";

    @TempDir Path temp;

    private RefreshStub stub;
    private PluginContext context;
    private BasisAuthPlugin plugin;
    private BasisStore store;

    @BeforeEach
    void setUp() throws Exception {
        stub = RefreshStub.start(CLIENT, SECRET, false);
        context = new PluginContext(temp, settings(stub, CLIENT, SECRET));
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
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    void refreshRotatesTokenWithBasicClientAuthentication() {
        AuthIdentity identity = plugin.refresh(context, SESSION);

        assertNotNull(identity);
        assertEquals("sub-1", identity.subject());
        assertEquals("refresh_token", stub.lastGrant());
        assertEquals("refresh-1", stub.lastRefresh());
        assertNotNull(stub.lastAuthorization());
        assertTrue(stub.lastAuthorization().startsWith("Basic "));
        assertEquals("refresh-2", stub.current());
        assertEquals("refresh-2", store.refreshToken(SESSION));
    }

    @Test
    void reloadAndReuseOfConsumedTokenRevokesTheSessionFamily() {
        plugin.refresh(context, SESSION);
        store.putRefreshToken(SESSION, "refresh-2");

        assertThrows(
                RefreshReuseException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        store.consumeAndReplace(SESSION, "refresh-1", "refresh-3");
                    }
                });
        assertNull(plugin.currentIdentity(context, SESSION));
        assertNull(store.refreshToken(SESSION));
    }

    @Test
    void invalidGrantClearsRefreshState() {
        stub.failRefresh(true);

        assertNull(plugin.refresh(context, SESSION));
        assertNull(store.refreshToken(SESSION));
    }

    @Test
    void rotatedRefreshTokenIsEncryptedAtRest() throws Exception {
        plugin.refresh(context, SESSION);

        Path file = temp.resolve("identity").resolve("basis-auth-store.json");
        String stored = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertFalse(stored.contains("refresh-2"));
        assertFalse(stored.contains("refresh-1"));
        assertEquals("refresh-2", store.refreshToken(SESSION));
    }

    @Test
    void publicClientSendsClientIdAndNoSecret() throws Exception {
        RefreshStub publicStub = RefreshStub.start(CLIENT, "", true);
        try {
            PluginContext publicContext =
                    new PluginContext(temp.resolve("public"), settings(publicStub, CLIENT, ""));
            BasisAuthPlugin publicPlugin = new BasisAuthPlugin();
            publicPlugin.initialize(publicContext);
            BasisStore publicStore =
                    (BasisStore) publicContext.service(BasisAuthPlugin.SERVICE_STORE);
            publicStore.putSession(
                    "public-session",
                    new AuthIdentity(
                            "basis-auth",
                            "sub-2",
                            "user@example.org",
                            "User",
                            true,
                            true,
                            new HashMap<String, String>()));
            publicStore.putRefreshToken("public-session", "refresh-1");

            assertNotNull(publicPlugin.refresh(publicContext, "public-session"));
            assertEquals(CLIENT, publicStub.lastClientId());
            assertNull(publicStub.lastAuthorization());
        } finally {
            publicStub.close();
        }
    }

    private static Map<String, String> settings(RefreshStub target, String client, String secret) {
        Map<String, String> values = new HashMap<String, String>();
        values.put("BASIS_AUTH_ISSUER", target.issuer());
        values.put("BASIS_AUTH_CLIENT_ID", client);
        if (secret.length() > 0) {
            values.put("BASIS_AUTH_CLIENT_SECRET", secret);
        }
        values.put("BASIS_AUTH_RESOURCE", RESOURCE);
        values.put("BASIS_AUTH_RESOURCE_SECRET", "resource-secret-1234567890");
        values.put("basis.offline-access", "true");
        return values;
    }
}
