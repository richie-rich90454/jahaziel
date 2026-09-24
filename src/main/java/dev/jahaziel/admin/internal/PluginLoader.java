/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import dev.jahaziel.admin.api.JahazielPlugin;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Loads plugins from {@code data/plugins/*.jar}. An absent directory means no plugins. */
@Component
public final class PluginLoader {
  private final List<JahazielPlugin> plugins;

  public PluginLoader(@Value("${jahaziel.plugins.dir:data/plugins}") String pluginsDir) {
    List<JahazielPlugin> found = new ArrayList<>();
    Path dir = Path.of(pluginsDir);
    if (Files.isDirectory(dir)) {
      try (var jars = Files.list(dir)) {
        List<URL> urls =
            jars.filter(path -> path.toString().endsWith(".jar"))
                .map(
                    path -> {
                      try {
                        return path.toUri().toURL();
                      } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                      }
                    })
                .toList();
        if (!urls.isEmpty()) {
          // ponytail: loader intentionally never closed; plugins live for the process lifetime.
          URLClassLoader loader =
              new URLClassLoader(urls.toArray(URL[]::new), PluginLoader.class.getClassLoader());
          ServiceLoader.load(JahazielPlugin.class, loader).forEach(found::add);
        }
      } catch (IOException failure) {
        throw new UncheckedIOException(failure);
      }
    }
    this.plugins = List.copyOf(found);
  }

  @PostConstruct
  void start() {
    plugins.forEach(plugin -> plugin.onStartup());
  }

  @PreDestroy
  void stop() {
    plugins.forEach(plugin -> plugin.onShutdown());
  }

  /** Returns the loaded plugins, empty when none exist. */
  public List<JahazielPlugin> plugins() {
    return plugins;
  }
}
