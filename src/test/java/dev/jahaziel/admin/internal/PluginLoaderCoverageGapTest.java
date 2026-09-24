/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class PluginLoaderCoverageGapTest {
  @TempDir private Path tmp;

  @Test
  void absentDirMeansNoPlugins() {
    PluginLoader loader = new PluginLoader(tmp.resolve("missing").toString());
    assertTrue(loader.plugins().isEmpty());
    loader.start();
    loader.stop();
  }

  @Test
  void emptyDirMeansNoPlugins() throws Exception {
    Path dir = tmp.resolve("empty");
    Files.createDirectories(dir);
    PluginLoader loader = new PluginLoader(dir.toString());
    assertTrue(loader.plugins().isEmpty());
    loader.start();
    loader.stop();
  }

  @Test
  void nonJarFilesAreIgnored() throws Exception {
    Path dir = tmp.resolve("nojars");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("note.txt"), "hi");
    PluginLoader loader = new PluginLoader(dir.toString());
    assertTrue(loader.plugins().isEmpty());
  }

  @Test
  void ghostJarLoadsEmpty() {
    Path jar = mock(Path.class);
    when(jar.toString()).thenReturn("ghost.jar");
    when(jar.toUri()).thenReturn(tmp.resolve("ghost.jar").toUri());
    Path txt = mock(Path.class);
    when(txt.toString()).thenReturn("note.txt");
    try (var files = Mockito.mockStatic(Files.class)) {
      files.when(() -> Files.isDirectory(any(Path.class))).thenReturn(true);
      files.when(() -> Files.list(any(Path.class))).thenReturn(Stream.of(jar, txt));
      PluginLoader loader = new PluginLoader("whatever");
      assertTrue(loader.plugins().isEmpty());
      loader.start();
      loader.stop();
    }
  }

  @Test
  void listFailureWraps() {
    try (var files = Mockito.mockStatic(Files.class)) {
      files.when(() -> Files.isDirectory(any(Path.class))).thenReturn(true);
      files.when(() -> Files.list(any(Path.class))).thenThrow(new IOException("boom"));
      assertThrows(UncheckedIOException.class, () -> new PluginLoader("whatever"));
    }
  }

  @Test
  void jarUrlFailureWraps() throws Exception {
    Path bad = mock(Path.class);
    when(bad.toString()).thenReturn("evil.jar");
    URI uri = mock(URI.class);
    when(bad.toUri()).thenReturn(uri);
    when(uri.toURL()).thenThrow(new MalformedURLException("boom"));
    try (var files = Mockito.mockStatic(Files.class)) {
      files.when(() -> Files.isDirectory(any(Path.class))).thenReturn(true);
      files.when(() -> Files.list(any(Path.class))).thenReturn(Stream.of(bad));
      assertThrows(UncheckedIOException.class, () -> new PluginLoader("whatever"));
    }
  }
}
