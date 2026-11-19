/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.config;

import dev.jahaziel.ops.OpsShutdownHook;

import java.util.Objects;

/** Attaches and detaches the graceful-drain hook with the JVM lifecycle. */
public final class OpsShutdownHookRegistrar {
    private final OpsShutdownHook hook;

    public OpsShutdownHookRegistrar(OpsShutdownHook hook) {
        this.hook = Objects.requireNonNull(hook, "hook");
    }

    /** Registers the hook when the context starts. */
    public void register() {
        Runtime.getRuntime().addShutdownHook(hook);
    }

    /** Removes the hook when the context stops. */
    public void unregister() {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException alreadyShuttingDown) {
            // The JVM is already shutting down, so the hook will run as intended.
        }
    }
}
