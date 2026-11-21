/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Corrupt or stale on-disk auth state must not break boot or grow without bound. */
final class BasisStoreDurabilityTest {
    @TempDir Path temp;

    @BeforeEach
    void useCanonicalTempDirectory() throws IOException {
        temp = temp.toRealPath();
    }

    @Test
    void corruptStoreIsQuarantinedAndLoadedEmpty() throws IOException {
        BasisConfig config = config();
        Files.createDirectories(config.identityDir());
        Path storeFile = config.identityDir().resolve("basis-auth-store.json");
        Files.writeString(storeFile, "{not json");

        BasisStore store = new BasisStore(config);

        assertNull(store.takePending("pending"));
        assertNull(store.getSession("session"));
        assertFalse(store.isDenied("jti", System.currentTimeMillis() / 1000L));
        assertTrue(Files.exists(storeFile.resolveSibling("basis-auth-store.json.corrupt")));
    }

    @Test
    void expiredPendingJtiAndSessionsArePrunedOnLoad() throws IOException {
        BasisConfig config = config();
        Files.createDirectories(config.identityDir());
        long nowMillis = System.currentTimeMillis();
        long nowSeconds = nowMillis / 1000L;
        String json =
                "{\"pending\":{"
                        + "\"live\":{\"nonce\":\"n\",\"verifier\":\"v\",\"redirectUri\":\"r\",\"expiresAt\":"
                        + (nowMillis + 600000L)
                        + "},"
                        + "\"old\":{\"nonce\":\"n\",\"verifier\":\"v\",\"redirectUri\":\"r\",\"expiresAt\":"
                        + (nowMillis - 1000L)
                        + "}},"
                        + "\"denied\":{"
                        + "\"live\":{\"validAfter\":"
                        + (nowSeconds - 10L)
                        + ",\"expiresAt\":"
                        + (nowSeconds + 600L)
                        + "},"
                        + "\"old\":{\"validAfter\":"
                        + (nowSeconds - 600L)
                        + ",\"expiresAt\":"
                        + (nowSeconds - 10L)
                        + "}},"
                        + "\"sessions\":{"
                        + "\"live\":{\"provider\":\"basis\",\"subject\":\"live\",\"accessTokenExpiresAt\":"
                        + (nowMillis + 600000L)
                        + "},"
                        + "\"old\":{\"provider\":\"basis\",\"subject\":\"old\",\"accessTokenExpiresAt\":"
                        + (nowMillis - 1000L)
                        + "}},"
                        + "\"refresh\":{}}";
        Files.writeString(config.identityDir().resolve("basis-auth-store.json"), json);

        BasisStore store = new BasisStore(config);

        assertNotNull(store.takePending("live"));
        assertNull(store.takePending("old"));
        assertTrue(store.isDenied("live", nowSeconds));
        assertFalse(store.isDenied("old", nowSeconds));
        assertNotNull(store.getSession("live"));
        assertNull(store.getSession("old"));

        store.putTokenExpiresAt("live", nowMillis + 600000L);
        String persisted = Files.readString(config.identityDir().resolve("basis-auth-store.json"));
        assertTrue(persisted.contains("\"live\""));
        assertFalse(persisted.contains("\"old\""));
    }

    @Test
    void corruptKeyFailsWithControlledException() throws IOException {
        final BasisConfig config = config();
        Files.createDirectories(config.keysDir());
        Files.writeString(config.keysDir().resolve("basis-auth.key"), "not-base64!!");

        assertThrows(
                BasisException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new BasisStore(config);
                    }
                });
    }

    private BasisConfig config() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("BASIS_AUTH_ISSUER", "https://auth.example.org");
        settings.put("BASIS_AUTH_CLIENT_ID", "example-web");
        settings.put("BASIS_AUTH_RESOURCE", "urn:example:api");
        return new BasisConfig(new PluginContext(temp, settings));
    }
}
