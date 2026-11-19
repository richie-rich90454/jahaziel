/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class PortDiscoveryPluginTest {
    private static final class FakeDiscovery extends PortDiscoveryPlugin {
        FakeDiscovery() {
            super(
                    new PluginDescriptor(
                            "fake-port-discovery",
                            "1.0",
                            new ArrayList<String>(),
                            new ArrayList<String>()));
        }

        @Override
        public void initialize(PluginContext context) {}

        @Override
        public void shutdown() {}

        @Override
        public List<Integer> listeningPorts(long pid) {
            List<Integer> ports = new ArrayList<Integer>();
            if (pid == 1L) {
                ports.add(Integer.valueOf(3000));
                ports.add(Integer.valueOf(8080));
            } else if (pid == 2L) {
                ports.add(Integer.valueOf(8080));
                ports.add(Integer.valueOf(5000));
            }
            return ports;
        }
    }

    @Test
    void nameComesFromDescriptor() {
        assertEquals("fake-port-discovery", new FakeDiscovery().name());
    }

    @Test
    void collectionHelperReturnsSortedDeduplicatedUnion() {
        List<Long> pids = Arrays.asList(Long.valueOf(2), Long.valueOf(1), Long.valueOf(2));
        assertEquals(
                Arrays.asList(Integer.valueOf(3000), Integer.valueOf(5000), Integer.valueOf(8080)),
                new FakeDiscovery().listeningPorts(pids));
    }

    @Test
    void nullAndUnmatchedPidsContributeNothing() {
        FakeDiscovery discovery = new FakeDiscovery();
        assertTrue(discovery.listeningPorts(null).isEmpty());
        List<Long> pids = Arrays.asList(null, Long.valueOf(99));
        assertTrue(discovery.listeningPorts(pids).isEmpty());
    }

    @Test
    void malformedAddressesAndPidsAreRejected() {
        assertEquals(-1, PortDiscoveryPlugin.parsePort("*:notaport", 10));
        assertEquals(-1, PortDiscoveryPlugin.parsePort("no-colon", 10));
        assertEquals(-1L, PortDiscoveryPlugin.parsePid("12x"));
        assertEquals(8080, PortDiscoveryPlugin.parsePort("*:1F90", 16));
    }
}
