/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.plugin;

import java.util.Objects;

/** Base contract for every extension point. Abstract class, never an interface. */
public abstract class Plugin {
    private final PluginDescriptor descriptor;

    protected Plugin(PluginDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    public final PluginDescriptor descriptor() {
        return descriptor;
    }

    public abstract void initialize(PluginContext context);

    public abstract void shutdown();
}
