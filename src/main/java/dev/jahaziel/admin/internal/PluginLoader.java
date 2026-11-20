/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.admin.internal;

import dev.jahaziel.admin.api.JahazielPlugin;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/** Loads plugins from {@code data/plugins/*.jar}. An absent directory means no plugins. */
@Component
public final class PluginLoader {
    private final List<JahazielPlugin> plugins;

    public PluginLoader(@Value("${jahaziel.plugins.dir:data/plugins}") String pluginsDir) {
        List<JahazielPlugin> found = new ArrayList<>();
        Path dir = Path.of(pluginsDir);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> jars = Files.list(dir)) {
                List<URL> urls = new ArrayList<URL>();
                Iterator<Path> paths = jars.iterator();
                while (paths.hasNext()) {
                    Path path = paths.next();
                    if (!path.toString().endsWith(".jar")) {
                        continue;
                    }
                    try {
                        urls.add(path.toUri().toURL());
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                }
                if (!urls.isEmpty()) {
                    // ponytail: loader intentionally never closed; plugins live for the process
                    // lifetime.
                    URLClassLoader loader =
                            new URLClassLoader(
                                    urls.toArray(new URL[0]), PluginLoader.class.getClassLoader());
                    ServiceLoader<JahazielPlugin> loaded =
                            ServiceLoader.load(JahazielPlugin.class, loader);
                    Iterator<JahazielPlugin> discovered = loaded.iterator();
                    while (discovered.hasNext()) {
                        found.add(discovered.next());
                    }
                }
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        this.plugins = List.copyOf(found);
    }

    @PostConstruct
    void start() {
        for (int index = 0; index < plugins.size(); index++) {
            plugins.get(index).onStartup();
        }
    }

    @PreDestroy
    void stop() {
        for (int index = 0; index < plugins.size(); index++) {
            plugins.get(index).onShutdown();
        }
    }

    /** Returns the loaded plugins, empty when none exist. */
    public List<JahazielPlugin> plugins() {
        return plugins;
    }
}
