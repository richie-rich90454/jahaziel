/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/** Final gap tests: dotenv shapes, OS/arch operands, and jar-dir fallbacks. */
class PlatformFinalTest {
  @TempDir private Path tmp;

  @Test
  void mapOsCoversNixAndNux() {
    assertEquals(Platform.Os.LINUX, Platforms.mapOs("prefix-nix"));
    assertEquals(Platform.Os.LINUX, Platforms.mapOs("prefix-nux"));
    assertEquals(Platform.Os.LINUX, Platforms.mapOs("linux"));
  }

  @Test
  void resolveJarDirHandlesJarWithoutParent() {
    File jar = Mockito.mock(File.class);
    Mockito.when(jar.getName()).thenReturn("jahaziel.jar");
    Mockito.when(jar.getAbsoluteFile()).thenReturn(jar);
    Mockito.when(jar.getParentFile()).thenReturn(null);
    try (var app =
        Mockito.mockConstruction(
            org.springframework.boot.system.ApplicationHome.class,
            (mock, context) -> Mockito.when(mock.getSource()).thenReturn(jar))) {
      assertEquals(
          Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
          Platforms.resolveJarDir());
    }
  }

  @Test
  void resolveJarDirSurvivesSecurityManager() {
    try (var app =
        Mockito.mockConstruction(
            org.springframework.boot.system.ApplicationHome.class,
            (mock, context) -> Mockito.when(mock.getSource()).thenThrow(new SecurityException()))) {
      assertEquals(
          Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
          Platforms.resolveJarDir());
    }
  }

  @Test
  void dotEnvParsesEveryShape() throws Exception {
    Path file = tmp.resolve(".env");
    Files.writeString(
        file,
        String.join(
            "\n",
            "PLAIN=1",
            "V=2",
            "export EXPORTED=2",
            "export BAD LINE",
            "NOEQUALS",
            "=empty-key",
            " =blank-key",
            "KEY WITH SPACE=x",
            "KEY\tTAB=x",
            "QUOTED=\"a # kept\"",
            "SINGLE='b # kept'",
            "HASHED=value # stripped",
            "NOSPACE=value#kept",
            "LEAD=#kept",
            "MIX=\"a'b\"",
            "MIX2='a\"b'",
            "SHORT=v",
            "MIS1=\"ab'",
            "MIS2='ab\"",
            "# comment",
            ""),
        StandardCharsets.UTF_8);
    Map<String, String> values = DotEnv.parse(file);
    assertEquals("1", values.get("PLAIN"));
    assertEquals("2", values.get("EXPORTED"));
    assertEquals("a # kept", values.get("QUOTED"));
    assertEquals("b # kept", values.get("SINGLE"));
    assertEquals("value", values.get("HASHED"));
    assertEquals("value#kept", values.get("NOSPACE"));
    assertEquals("#kept", values.get("LEAD"));
    assertEquals("a'b", values.get("MIX"));
    assertEquals("a\"b", values.get("MIX2"));
    assertEquals("v", values.get("SHORT"));
    assertEquals("\"ab'", values.get("MIS1"));
    assertEquals("'ab\"", values.get("MIS2"));
  }

  @Test
  void dotEnvMissingBasisVars() {
    assertEquals(5, DotEnv.missingBasisVars(Map.of()).size());
    assertTrue(DotEnv.missingBasisVars(Map.of()).contains("BASIS_AUTH_ISSUER"));
    Map<String, String> full =
        Map.of(
            "BASIS_AUTH_ISSUER",
            "i",
            "BASIS_REDIRECT_URI",
            "r",
            "BASIS_CLIENT_ID",
            "c",
            "BASIS_CLIENT_SECRET",
            "s",
            "BASIS_RESOURCE",
            "x");
    assertTrue(DotEnv.missingBasisVars(full).isEmpty());
    assertEquals(
        List.of("BASIS_CLIENT_SECRET"),
        DotEnv.missingBasisVars(
            Map.of(
                "BASIS_AUTH_ISSUER",
                "i",
                "BASIS_REDIRECT_URI",
                "r",
                "BASIS_CLIENT_ID",
                "c",
                "BASIS_CLIENT_SECRET",
                "  ",
                "BASIS_RESOURCE",
                "x")));
  }
}
