/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.identity.auth.AuthPluginRegistry;
import dev.jahaziel.identity.auth.AuthProviderPlugin;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class AuthProviderDispatcherTest {
    @Test
    void selectsFirstProviderInConfiguredOrder() {
        StubPlugin github = new StubPlugin("github");
        StubPlugin basis = new StubPlugin("basis-auth");
        AuthPluginRegistry registry = new AuthPluginRegistry();
        registry.register(github);
        registry.register(basis);

        AuthProviderConfig basisFirst =
                new AuthProviderConfig(Arrays.asList("basis-auth", "github", "missing"), null);
        AuthProviderDispatcher first = new AuthProviderDispatcher(registry, basisFirst);
        assertSame(basis, first.active());
        assertTrue(first.handles());

        AuthProviderConfig githubFirst =
                new AuthProviderConfig(Arrays.asList("github", "basis-auth"), null);
        AuthProviderDispatcher second = new AuthProviderDispatcher(registry, githubFirst);
        assertSame(github, second.active());
    }

    @Test
    void emptyConfigFallsBackEvenWhenRegistryHasProviders() {
        AuthPluginRegistry registry = new AuthPluginRegistry();
        registry.register(new StubPlugin("github"));
        registry.register(new StubPlugin("basis-auth"));
        AuthProviderConfig config = AuthProviderConfig.fromSettings(null, registry);
        assertTrue(config.isEmpty());
        AuthProviderDispatcher dispatcher = new AuthProviderDispatcher(registry, config);
        assertFalse(dispatcher.handles());
    }

    @Test
    void emptyConfigFallsBackEvenWhenProvidersAreRegistered() {
        AuthPluginRegistry registry = new AuthPluginRegistry();
        registry.register(new StubPlugin("github"));
        AuthProviderDispatcher dispatcher =
                new AuthProviderDispatcher(
                        registry, new AuthProviderConfig(Collections.<String>emptyList(), null));
        assertFalse(dispatcher.handles());
    }

    @Test
    void fallsBackWhenNoProviderIsEnabled() {
        AuthProviderDispatcher dispatcher =
                new AuthProviderDispatcher(
                        new AuthPluginRegistry(),
                        new AuthProviderConfig(Collections.<String>emptyList(), null));
        assertFalse(dispatcher.handles());
        assertNull(dispatcher.startLogin("state", "nonce", "https://app/cb"));
        assertNull(dispatcher.handleCallback(new HashMap<String, String>()));
        assertNull(dispatcher.currentIdentity("session"));
        dispatcher.endSession("session");
        assertNull(dispatcher.backchannel());
    }

    @Test
    void fallsBackWhenConfiguredProviderIsAbsent() {
        AuthPluginRegistry registry = new AuthPluginRegistry();
        registry.register(new StubPlugin("github"));
        AuthProviderConfig config = new AuthProviderConfig(Arrays.asList("missing-provider"), null);
        AuthProviderDispatcher dispatcher = new AuthProviderDispatcher(registry, config);
        assertFalse(dispatcher.handles());
        assertNull(dispatcher.startLogin("state", "nonce", "https://app/cb"));
    }

    @Test
    void delegatesAllLoginCallsToActiveProvider() {
        StubPlugin plugin = new StubPlugin("google");
        AuthPluginRegistry registry = new AuthPluginRegistry();
        registry.register(plugin);
        AuthProviderConfig config = new AuthProviderConfig(Arrays.asList("google"), null);
        AuthProviderDispatcher dispatcher = new AuthProviderDispatcher(registry, config);

        assertEquals(
                "https://idp.example/google",
                dispatcher.startLogin("state-1", "nonce-1", "https://app/cb"));
        assertEquals("state-1:nonce-1:https://app/cb", plugin.started);

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", "state-1");
        AuthIdentity callback = dispatcher.handleCallback(parameters);
        assertSame(plugin.identity, callback);
        assertEquals("state-1", plugin.callbackState);

        assertSame(plugin.identity, dispatcher.currentIdentity("session-1"));
        assertEquals("session-1", plugin.currentSession);

        dispatcher.endSession("session-1");
        assertEquals("session-1", plugin.endedSession);
    }

    @Test
    void activeResolvesExactlyOnceUnderConcurrentCallers() throws Exception {
        final StubPlugin plugin = new StubPlugin("google");
        AuthPluginRegistry registry = new AuthPluginRegistry();
        registry.register(plugin);
        final AuthProviderDispatcher dispatcher =
                new AuthProviderDispatcher(
                        registry, new AuthProviderConfig(Arrays.asList("google"), null));
        final int count = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(count);
        final List<AuthProviderPlugin> results =
                Collections.synchronizedList(new ArrayList<AuthProviderPlugin>());
        List<Thread> workers = new ArrayList<Thread>();
        for (int index = 0; index < count; index++) {
            Thread worker =
                    new Thread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        start.await();
                                        results.add(dispatcher.active());
                                    } catch (InterruptedException interrupted) {
                                        Thread.currentThread().interrupt();
                                    } finally {
                                        done.countDown();
                                    }
                                }
                            });
            workers.add(worker);
            worker.start();
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        for (int index = 0; index < results.size(); index++) {
            assertSame(plugin, results.get(index));
        }
        assertEquals(count, results.size());
    }

    private static final class StubPlugin extends AuthProviderPlugin {
        private final AuthIdentity identity;
        private String started;
        private String callbackState;
        private String currentSession;
        private String endedSession;

        private StubPlugin(String name) {
            super(
                    new PluginDescriptor(
                            name,
                            "1",
                            Collections.<String>emptyList(),
                            Collections.<String>emptyList()));
            identity =
                    new AuthIdentity(
                            name,
                            "subject-" + name,
                            name + "@example.org",
                            "Example User",
                            true,
                            true,
                            new HashMap<String, String>());
        }

        @Override
        public void initialize(PluginContext context) {}

        @Override
        public void shutdown() {}

        @Override
        public String startLogin(
                PluginContext context, String state, String nonce, String redirectUri) {
            started = state + ":" + nonce + ":" + redirectUri;
            return "https://idp.example/" + descriptor().name();
        }

        @Override
        public AuthIdentity handleCallback(PluginContext context, Map<String, String> parameters) {
            callbackState = parameters.get("state");
            return identity;
        }

        @Override
        public AuthIdentity currentIdentity(PluginContext context, String sessionId) {
            currentSession = sessionId;
            return identity;
        }

        @Override
        public void endSession(PluginContext context, String sessionId) {
            endedSession = sessionId;
        }
    }
}
