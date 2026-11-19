/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable metadata for one plugin. Plain class, no record, no enum. */
public final class PluginDescriptor {
    private final String name;
    private final String version;
    private final List<String> requires;
    private final List<String> capabilities;

    public PluginDescriptor(
            String name, String version, List<String> requires, List<String> capabilities) {
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        Objects.requireNonNull(requires, "requires");
        Objects.requireNonNull(capabilities, "capabilities");
        this.requires = Collections.unmodifiableList(new ArrayList<String>(requires));
        this.capabilities = Collections.unmodifiableList(new ArrayList<String>(capabilities));
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public List<String> requires() {
        return requires;
    }

    public List<String> capabilities() {
        return capabilities;
    }

    public boolean provides(String capability) {
        for (String candidate : capabilities) {
            if (candidate.equals(capability)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PluginDescriptor)) {
            return false;
        }
        PluginDescriptor that = (PluginDescriptor) other;
        return name.equals(that.name)
                && version.equals(that.version)
                && requires.equals(that.requires)
                && capabilities.equals(that.capabilities);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + requires.hashCode();
        result = 31 * result + capabilities.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return name + "@" + version;
    }
}
