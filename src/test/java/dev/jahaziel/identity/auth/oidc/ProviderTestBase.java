/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.oidc;

import dev.jahaziel.identity.auth.AuthIdentity;
import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Shared loopback plumbing for the concrete provider tests. */
abstract class ProviderTestBase {
    @TempDir Path tempDir;

    StubIdp idp;

    @BeforeEach
    void startStub() {
        idp = new StubIdp();
    }

    @AfterEach
    void stopStub() {
        idp.stop();
    }

    PluginContext context(String prefix) {
        Map<String, String> values = new HashMap<String, String>();
        values.put(prefix + ".issuer", idp.issuer());
        values.put(prefix + ".client-id", StubIdp.CLIENT_ID);
        values.put(prefix + ".client-secret", "test-secret");
        return new PluginContext(tempDir, values);
    }

    AuthIdentity completeLogin(
            OidcProviderPlugin plugin, PluginContext context, String state, String nonce) {
        plugin.startLogin(context, state, nonce, "http://app.test/callback");
        idp.setNextClaims(claims(nonce));
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("state", state);
        parameters.put("code", "code");
        return plugin.handleCallback(context, parameters);
    }

    private String claims(String nonce) {
        long expiry = System.currentTimeMillis() / 1000L + 600L;
        return "{\"iss\":\""
                + idp.issuer()
                + "\","
                + "\"aud\":\""
                + StubIdp.CLIENT_ID
                + "\","
                + "\"sub\":\"user-123\","
                + "\"nonce\":\""
                + nonce
                + "\","
                + "\"exp\":"
                + expiry
                + ","
                + "\"email\":\"user@example.test\","
                + "\"name\":\"Test User\","
                + "\"email_verified\":true,"
                + "\"amr\":[\"pwd\",\"otp\"]}";
    }
}
