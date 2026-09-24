/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.boot.system.ApplicationHome;

/** Final gap tests for template seeds, the jar-adjacent base, and the key wait loop. */
class DistFinalTest {
  @TempDir private Path tmp;

  @Test
  void skipsSeedMissingFromClasspath() throws Exception {
    URI seed = TemplateSeeds.class.getResource("/templates/empty/template.json").toURI();
    org.junit.jupiter.api.Assertions.assertEquals("file", seed.getScheme());
    Path seedDir = Path.of(seed).getParent();
    Path hidden = seedDir.resolveSibling("empty-hidden-by-test");
    Files.move(seedDir, hidden);
    try {
      Path templates = tmp.resolve("templates");
      TemplateSeeds.copyIfAbsent(templates);
      assertTrue(Files.notExists(templates.resolve("empty").resolve("template.json")));
      assertTrue(Files.isRegularFile(templates.resolve("node").resolve("template.json")));
    } finally {
      Files.move(hidden, seedDir);
    }
  }

  @Test
  void propagatesIoFailureInsideTry() throws Exception {
    Path templates = tmp.resolve("templates");
    Files.createDirectories(templates);
    Files.writeString(templates.resolve("empty"), "not-a-directory");
    assertThrows(IOException.class, () -> TemplateSeeds.copyIfAbsent(templates));
  }

  @Test
  void adjacentBaseFallsBackToUserDir() {
    try (var app =
        Mockito.mockConstruction(
            ApplicationHome.class,
            (mock, context) -> when(mock.getSource()).thenReturn(new File("classes")))) {
      assertEquals(
          Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
          Path.of(JarDataDir.adjacentBase()).toAbsolutePath().normalize());
    }
  }

  @Test
  void adjacentBaseHandlesJarWithoutParent() {
    File jar = Mockito.mock(File.class);
    when(jar.getName()).thenReturn("jahaziel.jar");
    when(jar.getAbsoluteFile()).thenReturn(jar);
    when(jar.getParentFile()).thenReturn(null);
    try (var app =
        Mockito.mockConstruction(
            ApplicationHome.class, (mock, context) -> when(mock.getSource()).thenReturn(jar))) {
      assertEquals(
          Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
          Path.of(JarDataDir.adjacentBase()).toAbsolutePath().normalize());
    }
  }

  @Test
  void adjacentBaseSurvivesSecurityManager() {
    try (var app =
        Mockito.mockConstruction(
            ApplicationHome.class,
            (mock, context) -> when(mock.getSource()).thenThrow(new SecurityException("x")))) {
      assertEquals(
          Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
          Path.of(JarDataDir.adjacentBase()).toAbsolutePath().normalize());
    }
  }

  @Test
  void adjacentBaseReturnsNullSourceToUserDir() {
    try (var app =
        Mockito.mockConstruction(
            ApplicationHome.class, (mock, context) -> when(mock.getSource()).thenReturn(null))) {
      assertEquals(
          Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
          Path.of(JarDataDir.adjacentBase()).toAbsolutePath().normalize());
    }
  }

  @Test
  void devKeysWaiterSeesKeysCreatedWhileWaiting() throws Exception {
    Path keys = tmp.resolve("keys");
    Files.createDirectories(keys);
    Path privateKey = keys.resolve("jwt.key");
    Path publicKey = keys.resolve("jwt.pub");
    Path lock = keys.resolve(".lock");
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.isRegularFile(privateKey)).thenReturn(false, false, true);
      files.when(() -> Files.isRegularFile(publicKey)).thenReturn(true);
      files
          .when(() -> Files.createFile(lock))
          .thenThrow(new java.nio.file.FileAlreadyExistsException("held"));
      DevKeys.Keys found = DevKeys.ensure(keys);
      assertEquals(privateKey, found.privateKey());
      assertEquals(publicKey, found.publicKey());
    } finally {
      Files.deleteIfExists(lock);
    }
  }

  @Test
  void devKeysInnerCheckSeesRacingKeys() throws Exception {
    Path keys = tmp.resolve("racing");
    Files.createDirectories(keys);
    Path privateKey = keys.resolve("jwt.key");
    Path publicKey = keys.resolve("jwt.pub");
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.isRegularFile(privateKey)).thenReturn(false, true);
      files.when(() -> Files.isRegularFile(publicKey)).thenReturn(true);
      DevKeys.Keys found = DevKeys.ensure(keys);
      assertEquals(privateKey, found.privateKey());
    }
  }
}
