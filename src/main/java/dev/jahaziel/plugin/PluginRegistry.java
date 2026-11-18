/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves plugin dependencies and drives lifecycle in dependency order. */
public final class PluginRegistry {
    private final Map<String, Plugin> byName = new LinkedHashMap<String, Plugin>();
    private boolean initialized;

    public void register(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        String name = plugin.descriptor().name();
        if (byName.containsKey(name)) {
            throw new IllegalStateException("duplicate plugin: " + name);
        }
        byName.put(name, plugin);
    }

    public Plugin find(String name) {
        return byName.get(name);
    }

    public List<Plugin> all() {
        return Collections.unmodifiableList(new ArrayList<Plugin>(byName.values()));
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void initializeAll(PluginContext context) {
        Objects.requireNonNull(context, "context");
        if (initialized) {
            return;
        }
        List<Plugin> ordered = resolveOrder();
        for (Plugin plugin : ordered) {
            plugin.initialize(context);
        }
        initialized = true;
    }

    public void shutdownAll() {
        List<Plugin> ordered = resolveOrder();
        for (int index = ordered.size() - 1; index >= 0; index--) {
            ordered.get(index).shutdown();
        }
        initialized = false;
    }

    private List<Plugin> resolveOrder() {
        List<Plugin> ordered = new ArrayList<Plugin>();
        Map<String, Integer> state = new HashMap<String, Integer>();
        for (String name : byName.keySet()) {
            visit(name, state, ordered);
        }
        return ordered;
    }

    private void visit(String name, Map<String, Integer> state, List<Plugin> ordered) {
        Integer current = state.get(name);
        if (current != null && current.intValue() == 1) {
            throw new IllegalStateException("plugin dependency cycle at " + name);
        }
        if (current != null && current.intValue() == 2) {
            return;
        }
        Plugin plugin = byName.get(name);
        if (plugin == null) {
            throw new IllegalStateException("missing required plugin: " + name);
        }
        state.put(name, Integer.valueOf(1));
        for (String required : plugin.descriptor().requires()) {
            visit(required, state, ordered);
        }
        state.put(name, Integer.valueOf(2));
        ordered.add(plugin);
    }
}
