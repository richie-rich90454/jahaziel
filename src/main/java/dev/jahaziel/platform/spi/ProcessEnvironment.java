/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.platform.spi;

import dev.jahaziel.plugin.Plugin;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strategy for launching and isolating workspace subprocesses on one host operating system.
 * Concrete strategies are plugins, so a deployment can contribute its own environment.
 */
public abstract class ProcessEnvironment extends Plugin {
    protected ProcessEnvironment(PluginDescriptor descriptor) {
        super(descriptor);
    }

    @Override
    public void initialize(PluginContext context) {
    }

    @Override
    public void shutdown() {
    }

    /** Returns whether this environment can run workspace processes on the current host. */
    public abstract boolean isSupported();

    /** Returns the command vector that starts a shell for a workspace session. */
    public List<String> shellCommand(List<String> command) {
        Objects.requireNonNull(command, "command");
        List<String> result = new ArrayList<String>();
        result.addAll(shellPrefix());
        result.addAll(command);
        return result;
    }

    /** Returns the subprocess environment with {@code extra} applied over the host defaults. */
    public Map<String, String> environment(Path home, Map<String, String> extra) {
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(extra, "extra");
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put(homeVariable(), home.toString());
        result.put(pathVariable(), pathValue());
        addDefaults(result);
        for (Map.Entry<String, String> entry : extra.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** Returns a command wrapped for a dedicated process session on this host. */
    public List<String> sessionWrapper(List<String> command) {
        Objects.requireNonNull(command, "command");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("managed process command must not be empty");
        }
        List<String> prefix = sessionPrefix();
        List<String> result = new ArrayList<String>(prefix.size() + command.size());
        result.addAll(prefix);
        result.addAll(command);
        return Collections.unmodifiableList(result);
    }

    /** Returns the directory holding per-process entries, or null when the host has none. */
    public Path processRoot() {
        return null;
    }

    /** Returns the home variable name used by this host. */
    public String homeVariable() {
        return "HOME";
    }

    /** Returns the PATH variable name used by this host. */
    public String pathVariable() {
        return "PATH";
    }

    /** Returns the default executable search path for this host. */
    public abstract String pathValue();

    /** Returns whether POSIX-only variables belong in this host's subprocess environment. */
    public boolean supportsPosixEnvironment() {
        return true;
    }

    /** Returns tool and profile overrides for one user's interactive session. */
    public Map<String, String> toolEnvironment(Path toolBin, Path profile) {
        Map<String, String> extra = new LinkedHashMap<String, String>();
        if (toolBin != null) {
            extra.put(pathVariable(), toolBin + File.pathSeparator + pathValue());
        }
        if (profile != null && supportsPosixEnvironment()) {
            extra.put("XDG_CONFIG_HOME", profile.resolve("config").toString());
            extra.put("XDG_DATA_HOME", profile.resolve("data").toString());
            extra.put("XDG_CACHE_HOME", profile.resolve("cache").toString());
        }
        return extra;
    }

    protected abstract List<String> shellPrefix();

    protected void addDefaults(Map<String, String> environment) {
        environment.put("TERM", "xterm-256color");
        environment.put("LANG", "C.UTF-8");
    }

    protected List<String> sessionPrefix() {
        return new ArrayList<String>();
    }
}
