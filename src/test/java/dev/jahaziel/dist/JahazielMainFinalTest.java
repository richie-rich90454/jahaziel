/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.platform.Platforms;
import dev.jahaziel.platform.TestEnv;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

  @Test
  void everyBasisVarMapsToAProperty() {
    for (String key : dev.jahaziel.platform.DotEnv.BASIS_VARS) {
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
            data.root().toString(), 11451, 8081, "code.example.com", false, false, false);
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
      try (var walk = Files.walk(dataRoot)) {
        walk.sorted(java.util.Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.deleteIfExists(path);
                  } catch (Exception ignored) {
                    // Best-effort test cleanup.
                  }
                });
      } catch (Exception ignored) {
        // Best-effort test cleanup.
      }
    }
  }
}
