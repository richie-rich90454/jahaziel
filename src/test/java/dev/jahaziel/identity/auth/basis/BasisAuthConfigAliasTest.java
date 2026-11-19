/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.plugin.PluginContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** The canonical BASIS_AUTH_* names and the legacy spelling both configure the provider. */
final class BasisAuthConfigAliasTest {
    @TempDir Path temp;

    @Test
    void canonicalAliasesAreRead() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("BASIS_AUTH_ISSUER", "https://auth.example.org");
        settings.put("BASIS_AUTH_CLIENT_ID", "example-web");
        settings.put("BASIS_AUTH_CLIENT_SECRET", "client-secret");
        settings.put("BASIS_AUTH_RESOURCE", "urn:example:api");
        settings.put("BASIS_AUTH_RESOURCE_SECRET", "resource-secret");

        BasisConfig config = new BasisConfig(new PluginContext(temp, settings));

        assertEquals("https://auth.example.org", config.issuer());
        assertEquals("example-web", config.clientId());
        assertEquals("client-secret", config.clientSecret());
        assertEquals("urn:example:api", config.resource());
        assertEquals("resource-secret", config.resourceSecret());
        assertFalse(config.publicClient());
    }

    @Test
    void legacyKeysStillWork() {
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("basis.auth-issuer", "https://auth.example.org");
        settings.put("basis.client-id", "example-web");
        settings.put("basis.resource", "urn:example:api");

        BasisConfig config = new BasisConfig(new PluginContext(temp, settings));

        assertEquals("example-web", config.clientId());
        assertEquals("urn:example:api", config.resource());
        assertTrue(config.publicClient());
    }
}
