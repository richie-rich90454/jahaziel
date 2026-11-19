/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.local;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.LocalPasswordPlugin;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

final class LocalPasswordPendingTtlTest {
    private static final long NOW = 1700000000L;
    private static final String REDIRECT = "https://app.example.org/callback";

    @TempDir Path temp;

    @Test
    void freshPendingIsAccepted() {
        LocalStore store = store(NOW);
        store.putPending("fresh", "nonce", REDIRECT, NOW);
        assertTrue(store.takePending("fresh"));
    }

    @Test
    void pendingAtExactlyTenMinutesIsStillAccepted() {
        LocalStore store = store(NOW);
        store.putPending("edge", "nonce", REDIRECT, NOW - 600L);
        assertTrue(store.takePending("edge"));
    }

    @Test
    void expiredPendingIsRejectedAndDeleted() throws Exception {
        LocalStore store = store(NOW);
        store.putPending("stale", "nonce", REDIRECT, NOW - 601L);

        assertFalse(store.takePending("stale"));
        byte[] raw = Files.readAllBytes(temp.resolve("local").resolve("pending.json"));
        assertFalse(new String(raw, StandardCharsets.UTF_8).contains("stale"));
    }

    @Test
    void pluginUsesInjectedClockForPendingExpiry() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("local.username", "admin");
        settings.put("local.password", "s3cret!");

        PluginContext early = new PluginContext(temp, settings);
        early.putService(
                LocalPasswordPlugin.SERVICE_CLOCK,
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        LocalPasswordPlugin first = new LocalPasswordPlugin();
        first.initialize(early);
        first.startLogin(early, "aged", "nonce", REDIRECT);

        PluginContext late = new PluginContext(temp, settings);
        late.putService(
                LocalPasswordPlugin.SERVICE_CLOCK,
                Clock.fixed(Instant.ofEpochSecond(NOW + 601L), ZoneOffset.UTC));
        LocalPasswordPlugin second = new LocalPasswordPlugin();
        second.initialize(late);

        final Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", "aged");
        parameters.put("username", "admin");
        parameters.put("password", "s3cret!");
        LocalAuthException failure =
                assertThrows(
                        LocalAuthException.class,
                        new org.junit.jupiter.api.function.Executable() {
                            @Override
                            public void execute() {
                                second.handleCallback(late, parameters);
                            }
                        });
        assertNotNull(failure.getMessage());
        assertTrue(failure.getMessage().contains("state"));
    }

    private LocalStore store(long now) {
        Map<String, String> settings = new HashMap<String, String>();
        return new LocalStore(
                new PluginContext(temp, settings),
                Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC));
    }
}
