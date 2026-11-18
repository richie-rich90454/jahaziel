/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.plugin;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Services and configuration handed to plugins. All state stays jar-adjacent. */
public final class PluginContext {
    private final Path dataDir;
    private final Map<String, String> settings;
    private final Map<String, Object> services = new HashMap<String, Object>();

    public PluginContext(Path dataDir, Map<String, String> settings) {
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir").toAbsolutePath().normalize();
        Objects.requireNonNull(settings, "settings");
        this.settings = Collections.unmodifiableMap(new HashMap<String, String>(settings));
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path pluginDir() {
        return dataDir.resolve("plugins");
    }

    public String setting(String key) {
        return settings.get(key);
    }

    public Map<String, String> settings() {
        return settings;
    }

    public void putService(String name, Object service) {
        services.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(service, "service"));
    }

    public Object service(String name) {
        return services.get(name);
    }
}
