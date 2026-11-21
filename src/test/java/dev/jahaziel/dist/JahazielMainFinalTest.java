/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.platform.Platforms;
import dev.jahaziel.platform.TestEnv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Final gap tests: real-env branches, the BASIS invariant, version, and setIfAbsent. */
class JahazielMainFinalTest {
    @TempDir private Path tmp;

    private static void clearBasisProps() {
        for (String key : dev.jahaziel.platform.DotEnv.BASIS_VARS) {
            System.clearProperty(key);
            String property = JahazielMain.basisProperty(key);
            if (property != null) {
                System.clearProperty(property);
            }
        }
    }

    private static void clearAdded(Set<String> before) {
        for (String key : new HashSet<>(System.getProperties().stringPropertyNames())) {
            if (!before.contains(key)) {
                System.clearProperty(key);
            }
        }
    }

    private static void clearAllBasisProps() {
        for (String key :
                java.util.List.of(
                        "BASIS_AUTH_ISSUER",
                        "BASIS_AUTH_CLIENT_ID",
                        "BASIS_AUTH_CLIENT_SECRET",
                        "BASIS_AUTH_RESOURCE",
                        "BASIS_AUTH_RESOURCE_SECRET",
                        "BASIS_AUTH_INTERNAL_URL",
                        "BASIS_AUTH_COOKIE_SECRET",
                        "CURRENT_URL_ORIGIN",
                        "BASIS_REDIRECT_URI",
                        "BASIS_CLIENT_ID",
                        "BASIS_CLIENT_SECRET",
                        "BASIS_RESOURCE",
                        "basis.auth-issuer",
                        "basis.client-id",
                        "basis.client-secret",
                        "basis.resource",
                        "basis.resource-secret",
                        "basis.internal-url",
                        "basis.cookie-secret",
                        "basis.current-url-origin",
                        "basis.redirect-uri")) {
            System.clearProperty(key);
        }
    }

    @Test
    void canonicalOnlyEnvMapsEveryDottedBasisProperty() {
        Set<String> before = new HashSet<>(System.getProperties().stringPropertyNames());
        clearAllBasisProps();
        try {
            Map<String, String> canonical =
                    Map.of(
                            "BASIS_AUTH_ISSUER",
                            "https://idp.invalid",
                            "BASIS_AUTH_CLIENT_ID",
                            "client",
                            "BASIS_AUTH_CLIENT_SECRET",
                            "secret",
                            "BASIS_AUTH_RESOURCE",
                            "urn:api",
                            "BASIS_AUTH_RESOURCE_SECRET",
                            "resource-secret",
                            "BASIS_AUTH_INTERNAL_URL",
                            "http://127.0.0.1:3001",
                            "BASIS_AUTH_COOKIE_SECRET",
                            "01234567890123456789012345678901",
                            "CURRENT_URL_ORIGIN",
                            "https://app.invalid");
            JahazielMain.applyDotEnv(canonical);
            assertEquals("https://idp.invalid", System.getProperty("basis.auth-issuer"));
            assertEquals("client", System.getProperty("basis.client-id"));
            assertEquals("secret", System.getProperty("basis.client-secret"));
            assertEquals("urn:api", System.getProperty("basis.resource"));
            assertEquals("resource-secret", System.getProperty("basis.resource-secret"));
            assertEquals("http://127.0.0.1:3001", System.getProperty("basis.internal-url"));
            assertEquals(
                    "01234567890123456789012345678901", System.getProperty("basis.cookie-secret"));
            assertEquals("https://app.invalid", System.getProperty("basis.current-url-origin"));
            assertEquals(
                    "https://app.invalid/api/auth/basis/callback",
                    System.getProperty("basis.redirect-uri"));
        } finally {
            clearAdded(before);
        }
    }

    @Test
    void canonicalNameWinsOverLegacyAliasInFile() {
        Set<String> before = new HashSet<>(System.getProperties().stringPropertyNames());
        clearAllBasisProps();
        try {
            Map<String, String> mixed =
                    Map.of(
                            "BASIS_AUTH_CLIENT_ID", "canonical",
                            "BASIS_CLIENT_ID", "legacy");
            JahazielMain.applyDotEnv(mixed);
            assertEquals("canonical", System.getProperty("basis.client-id"));
        } finally {
            clearAdded(before);
        }
    }

    @Test
    void realEnvCanonicalWinsOverFileValue() {
        Set<String> before = new HashSet<>(System.getProperties().stringPropertyNames());
        clearAllBasisProps();
        String prior = System.getenv("BASIS_AUTH_RESOURCE");
        try {
            TestEnv.set("BASIS_AUTH_RESOURCE", "urn:env");
            JahazielMain.applyDotEnv(Map.of("BASIS_AUTH_RESOURCE", "urn:file"));
            assertEquals("urn:env", System.getProperty("basis.resource"));
        } finally {
            TestEnv.set("BASIS_AUTH_RESOURCE", prior);
            clearAdded(before);
        }
    }

    @Test
    void uppercaseBootKeysMapToDottedProperties() {
        Set<String> before = new HashSet<>(System.getProperties().stringPropertyNames());
        String[] envKeys = {
            "JAHAZIEL_DEMO",
            "JAHAZIEL_SHARE_SECRET",
            "JAHAZIEL_JWT_TTL_SECONDS",
            "JAHAZIEL_LOG_LEVEL",
            "JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE"
        };
        java.util.Map<String, String> prior = new java.util.HashMap<>();
        for (String key : envKeys) {
            prior.put(key, System.getenv(key));
            TestEnv.clear(key);
        }
        for (String key :
                java.util.List.of(
                        "jahaziel.demo",
                        "jahaziel.share.secret",
                        "jahaziel.jwt.ttl-seconds",
                        "logging.level.root",
                        "jahaziel.language-tools.node-executable")) {
            System.clearProperty(key);
        }
        try {
            JahazielMain.applyDotEnv(
                    Map.of(
                            "JAHAZIEL_DEMO",
                            "true",
                            "JAHAZIEL_SHARE_SECRET",
                            "share",
                            "JAHAZIEL_JWT_TTL_SECONDS",
                            "1200",
                            "JAHAZIEL_LOG_LEVEL",
                            "DEBUG",
                            "JAHAZIEL_LANGUAGE_TOOLS_NODE_EXECUTABLE",
                            "/opt/node"));
            assertEquals("true", System.getProperty("jahaziel.demo"));
            assertEquals("share", System.getProperty("jahaziel.share.secret"));
            assertEquals("1200", System.getProperty("jahaziel.jwt.ttl-seconds"));
            assertEquals("DEBUG", System.getProperty("logging.level.root"));
            assertEquals(
                    "/opt/node", System.getProperty("jahaziel.language-tools.node-executable"));
        } finally {
            for (String key : envKeys) {
                TestEnv.set(key, prior.get(key));
            }
            clearAdded(before);
        }
    }

    @Test
    void shortCookieSecretFailsFast() {
        Set<String> before = new HashSet<>(System.getProperties().stringPropertyNames());
        clearAllBasisProps();
        try {
            IllegalArgumentException failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    JahazielMain.applyDotEnv(
                                            Map.of("BASIS_AUTH_COOKIE_SECRET", "short")));
            assertTrue(failure.getMessage().contains("32"));
        } finally {
            clearAdded(before);
        }
    }

    @Test
    void everyBasisVarMapsToAProperty() {
        for (String key : dev.jahaziel.platform.DotEnv.BASIS_VARS) {
            assertNotNull(JahazielMain.basisProperty(key), key);
        }
    }

    @Test
    void everyCanonicalBasisVarMapsToAProperty() {
        for (String key : dev.jahaziel.platform.DotEnv.BASIS_ALIAS_VARS) {
            assertNotNull(JahazielMain.basisProperty(key), key);
        }
    }

    @Test
    void applyDotEnvPrefersRealEnvOverFile() {
        clearBasisProps();
        String before = System.getenv("BASIS_AUTH_ISSUER");
        try {
            TestEnv.set("BASIS_AUTH_ISSUER", "https://env.invalid");
            Map<String, String> fileEnv = Map.of("BASIS_AUTH_ISSUER", "https://file.invalid");
            JahazielMain.applyDotEnv(fileEnv);
            assertEquals("https://env.invalid", System.getProperty("basis.auth-issuer"));
        } finally {
            TestEnv.set("BASIS_AUTH_ISSUER", before);
            clearBasisProps();
        }
    }

    @Test
    void missingBasisVarsSeesRealEnv() {
        clearBasisProps();
        String before = System.getenv("BASIS_CLIENT_ID");
        try {
            Map<String, String> fileEnv = new HashMap<>();
            assertTrue(JahazielMain.missingBasisVars(fileEnv).contains("BASIS_CLIENT_ID"));
            TestEnv.set("BASIS_CLIENT_ID", "from-env");
            assertFalse(JahazielMain.missingBasisVars(fileEnv).contains("BASIS_CLIENT_ID"));
        } finally {
            TestEnv.set("BASIS_CLIENT_ID", before);
            clearBasisProps();
        }
    }

    @Test
    void versionOfCoversBothSides() {
        assertEquals("0.1.0-SNAPSHOT (development)", JahazielMain.versionOf(null));
        Package pkg = mock(Package.class);
        when(pkg.getImplementationVersion()).thenReturn(null);
        assertEquals("0.1.0-SNAPSHOT (development)", JahazielMain.versionOf(pkg));
        when(pkg.getImplementationVersion()).thenReturn("9.9.9");
        assertEquals("9.9.9", JahazielMain.versionOf(pkg));
    }

    @Test
    void versionFallsBackInTests() {
        assertEquals("0.1.0-SNAPSHOT (development)", JahazielMain.version());
    }

    @Test
    void setIfAbsentHonoursRealEnv() throws Exception {
        Path dataRoot = tmp.resolve("data");
        JarDataDir data = JarDataDir.resolve(dataRoot.toString());
        DevKeys.Keys keys = DevKeys.ensure(data.keys());
        JahazielMain.Arguments parsed =
                new JahazielMain.Arguments(
                        data.root().toString(),
                        11451,
                        8081,
                        "code.example.com",
                        false,
                        false,
                        false);
        dev.jahaziel.platform.Platform platform = Platforms.detect(data.root().toString());
        Set<String> before = new HashSet<>(System.getProperties().stringPropertyNames());
        String envBefore = System.getenv("JAHAZIEL_DIST_ENABLED");
        try {
            JahazielMain.portableProperties(data, keys, parsed, platform);
            assertEquals("true", System.getProperty("jahaziel.dist.enabled"));
            for (String key : new HashSet<>(System.getProperties().stringPropertyNames())) {
                if (!before.contains(key)) {
                    System.clearProperty(key);
                }
            }
            TestEnv.set("JAHAZIEL_DIST_ENABLED", "env-wins");
            JahazielMain.portableProperties(data, keys, parsed, platform);
            assertNull(System.getProperty("jahaziel.dist.enabled"));
        } finally {
            TestEnv.set("JAHAZIEL_DIST_ENABLED", envBefore);
            for (String key : new HashSet<>(System.getProperties().stringPropertyNames())) {
                if (!before.contains(key)) {
                    System.clearProperty(key);
                }
            }
            List<Path> paths = new ArrayList<>();
            try (java.util.stream.Stream<Path> walk = Files.walk(dataRoot)) {
                java.util.Iterator<Path> iterator = walk.iterator();
                while (iterator.hasNext()) {
                    paths.add(iterator.next());
                }
            } catch (Exception ignored) {
                // Best-effort test cleanup.
            }
            paths.sort(java.util.Comparator.reverseOrder());
            for (Path path : paths) {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // Best-effort test cleanup.
                }
            }
        }
    }
}
