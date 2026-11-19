/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import dev.jahaziel.platform.Platform;
import dev.jahaziel.platform.Platforms;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects the port discovery strategy for the current operating system and reports the listening
 * ports of a set of process ids. Detection and parsing failures fail closed with an empty result.
 */
final class PortDiscoveries {
    private final PortDiscoveryPlugin plugin;

    PortDiscoveries() {
        this(detectPlugin());
    }

    PortDiscoveries(Platform.Os os) {
        this(pluginFor(os));
    }

    PortDiscoveries(PortDiscoveryPlugin plugin) {
        this.plugin = plugin;
    }

    /** Listening ports owned by the supplied process ids, or an empty list when unsupported. */
    List<Integer> candidates(List<Long> pids) {
        if (plugin == null) {
            return new ArrayList<Integer>();
        }
        return plugin.listeningPorts(pids);
    }

    static PortDiscoveryPlugin pluginFor(Platform.Os os) {
        if (os == Platform.Os.LINUX) {
            return new LinuxPortDiscovery();
        }
        if (os == Platform.Os.MACOS) {
            return new MacPortDiscovery();
        }
        if (os == Platform.Os.WINDOWS) {
            return new WindowsPortDiscovery();
        }
        return null;
    }

    private static PortDiscoveryPlugin detectPlugin() {
        try {
            return pluginFor(Platforms.detect(null).os());
        } catch (IllegalStateException unsupported) {
            return null;
        }
    }
}
