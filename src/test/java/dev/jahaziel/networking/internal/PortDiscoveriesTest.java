/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.platform.Platform;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class PortDiscoveriesTest {
    @Test
    void selectsLinuxPluginForLinux() {
        PortDiscoveryPlugin plugin = PortDiscoveries.pluginFor(Platform.Os.LINUX);
        assertTrue(plugin instanceof LinuxPortDiscovery);
        assertEquals("linux-port-discovery", plugin.name());
    }

    @Test
    void selectsMacPluginForMacos() {
        PortDiscoveryPlugin plugin = PortDiscoveries.pluginFor(Platform.Os.MACOS);
        assertTrue(plugin instanceof MacPortDiscovery);
        assertEquals("mac-port-discovery", plugin.name());
    }

    @Test
    void selectsWindowsPluginForWindows() {
        PortDiscoveryPlugin plugin = PortDiscoveries.pluginFor(Platform.Os.WINDOWS);
        assertTrue(plugin instanceof WindowsPortDiscovery);
        assertEquals("windows-port-discovery", plugin.name());
    }

    @Test
    void unknownOsFailsClosed() {
        assertNull(PortDiscoveries.pluginFor(null));
        List<Long> pids = Arrays.asList(Long.valueOf(10), Long.valueOf(20));
        assertTrue(new PortDiscoveries((PortDiscoveryPlugin) null).candidates(pids).isEmpty());
        assertTrue(new PortDiscoveries((Platform.Os) null).candidates(pids).isEmpty());
    }

    @Test
    void emptyPidsYieldEmptyCandidates() {
        assertTrue(
                new PortDiscoveries(Platform.Os.LINUX).candidates(new ArrayList<Long>()).isEmpty());
    }
}
