/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.admin.api.JahazielPlugin;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

/** Final gap tests: a real plugin jar on disk starts and stops its provider. */
class PluginLoaderFinalTest {
  /** Provider loaded from the parent loader; only the services entry lives in the jar. */
  public static class TestPlugin implements JahazielPlugin {
    static final AtomicBoolean STARTED = new AtomicBoolean();
    static final AtomicBoolean STOPPED = new AtomicBoolean();

    @Override
    public void onStartup() {
      STARTED.set(true);
    }

    @Override
    public void onShutdown() {
      STOPPED.set(true);
    }
  }

  private static Path pluginJar(Path dir) throws Exception {
    Files.createDirectories(dir);
    Path jar = dir.resolve("test-plugin.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
      JarEntry entry = new JarEntry("META-INF/services/" + JahazielPlugin.class.getName());
      out.putNextEntry(entry);
      out.write(TestPlugin.class.getName().getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    return jar;
  }

  @Test
  void loadsStartsAndStopsJarPlugins() throws Exception {
    TestPlugin.STARTED.set(false);
    TestPlugin.STOPPED.set(false);
    // Under target/ (never @TempDir): the loader intentionally never closes the jar, and Windows
    // locks open jars, which would fail @TempDir cleanup. `mvn clean` reclaims this directory.
    Path plugins =
        Path.of(System.getProperty("user.dir"), "target", "test-plugins-final")
            .resolve(UUID.randomUUID().toString());
    pluginJar(plugins);
    PluginLoader loader = new PluginLoader(plugins.toString());
    assertEquals(1, loader.plugins().size());
    loader.start();
    assertTrue(TestPlugin.STARTED.get());
    loader.stop();
    assertTrue(TestPlugin.STOPPED.get());
  }
}
