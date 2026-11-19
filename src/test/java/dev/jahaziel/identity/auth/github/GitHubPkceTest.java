/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.identity.auth.GitHubAuthPlugin;
import dev.jahaziel.identity.auth.basis.Pkce;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class GitHubPkceTest {
    private static final String REDIRECT = "https://app.example.org/oauth/github";

    @TempDir Path temp;

    private LoopbackGitHub stub;
    private PluginContext context;
    private GitHubAuthPlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        stub = LoopbackGitHub.start();
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("github.client-id", "octo-client");
        settings.put("github.client-secret", "octo-secret");
        settings.put("github.authorize-url", stub.base() + "/login/oauth/authorize");
        settings.put("github.token-url", stub.base() + "/login/oauth/access_token");
        settings.put("github.user-url", stub.base() + "/user");
        settings.put("github.emails-url", stub.base() + "/user/emails");
        context = new PluginContext(temp, settings);
        plugin = new GitHubAuthPlugin();
        plugin.initialize(context);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    void authorizeUrlCarriesS256ChallengeForVerifierSentAtExchange() {
        String url = plugin.startLogin(context, "pkce-state", "nonce", REDIRECT);
        String challenge = value(url, "code_challenge");
        assertEquals("S256", value(url, "code_challenge_method"));
        assertNotNull(challenge);

        plugin.handleCallback(context, callback("pkce-state"));

        String verifier = stub.tokenFormValue("code_verifier");
        assertNotNull(verifier);
        assertEquals(Pkce.challenge(verifier), challenge);
    }

    @Test
    void stateCannotBeReplayedToExchangeASecondTime() {
        plugin.startLogin(context, "replay-state", "nonce", REDIRECT);
        plugin.handleCallback(context, callback("replay-state"));

        final Map<String, String> replay = callback("replay-state");
        GitHubException failure =
                assertThrows(
                        GitHubException.class,
                        new org.junit.jupiter.api.function.Executable() {
                            @Override
                            public void execute() {
                                plugin.handleCallback(context, replay);
                            }
                        });
        assertNotNull(failure.getMessage());
        assertEquals(1, stub.tokenHits());
    }

    private static Map<String, String> callback(String state) {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", state);
        parameters.put("code", "good-code");
        return parameters;
    }

    private static String value(String url, String name) {
        String query = url.contains("?") ? url.substring(url.indexOf('?') + 1) : "";
        String[] pairs = query.split("&");
        for (int index = 0; index < pairs.length; index++) {
            int equal = pairs[index].indexOf('=');
            if (equal > 0 && pairs[index].substring(0, equal).equals(name)) {
                return URLDecoder.decode(pairs[index].substring(equal + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
