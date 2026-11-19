/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

class WindowsPortDiscoveryTest {
    private static final List<String> NETSTAT =
            Arrays.asList(
                    "Proto  Local Address          Foreign Address        State           PID",
                    "TCP    0.0.0.0:8080           0.0.0.0:0              LISTENING       1234",
                    "TCP    [::]:8080              [::]:0                 LISTENING       1234",
                    "TCP    127.0.0.1:3000         0.0.0.0:0              LISTENING       5678",
                    "TCP    127.0.0.1:3000         0.0.0.0:0              LISTENING       5678",
                    "TCP    10.0.0.5:54321         1.2.3.4:80             ESTABLISHED     1234",
                    "UDP    0.0.0.0:53             0.0.0.0:0              LISTENING       9999",
                    "TCP    0.0.0.0:notaport        0.0.0.0:0              LISTENING       1234",
                    "TCP    0.0.0.0:9999           0.0.0.0:0              LISTENING       nope");

    private static final class UnsupportedDiscovery extends WindowsPortDiscovery {
        @Override
        protected boolean supportedOs() {
            return false;
        }
    }

    private static final class BoundedDiscovery extends WindowsPortDiscovery {
        @Override
        protected int maxLines() {
            return 3;
        }

        @Override
        protected boolean supportedOs() {
            return false;
        }
    }

    @Test
    void parsesIpv4AndIpv6ListeningRows() {
        Map<Long, List<Integer>> parsed = new WindowsPortDiscovery().parseNetstat(NETSTAT);
        assertEquals(Arrays.asList(Integer.valueOf(8080)), parsed.get(Long.valueOf(1234)));
        assertEquals(Arrays.asList(Integer.valueOf(3000)), parsed.get(Long.valueOf(5678)));
    }

    @Test
    void skipsMalformedNonListeningAndNonTcpRows() {
        Map<Long, List<Integer>> parsed = new WindowsPortDiscovery().parseNetstat(NETSTAT);
        assertFalse(parsed.containsKey(Long.valueOf(9999)));
        for (Map.Entry<Long, List<Integer>> entry : parsed.entrySet()) {
            assertFalse(entry.getValue().contains(Integer.valueOf(54321)));
            assertFalse(entry.getValue().contains(Integer.valueOf(53)));
        }
    }

    @Test
    void duplicateRowsForOnePidAreSuppressed() {
        List<String> recorded = new ArrayList<String>();
        recorded.add("TCP    0.0.0.0:4000   0.0.0.0:0   LISTENING   42");
        recorded.add("TCP    [::]:4000      [::]:0      LISTENING   42");
        Map<Long, List<Integer>> parsed = new WindowsPortDiscovery().parseNetstat(recorded);
        assertEquals(Arrays.asList(Integer.valueOf(4000)), parsed.get(Long.valueOf(42)));
    }

    @Test
    void boundedParsingOnlyInspectsConfiguredNumberOfLines() {
        Map<Long, List<Integer>> parsed = new BoundedDiscovery().parseNetstat(NETSTAT);
        assertEquals(1, parsed.size());
        assertTrue(parsed.containsKey(Long.valueOf(1234)));
        assertFalse(parsed.containsKey(Long.valueOf(5678)));
    }

    @Test
    void emptyOutputYieldsNoPorts() {
        assertTrue(new WindowsPortDiscovery().parseNetstat(new ArrayList<String>()).isEmpty());
    }

    @Test
    void unsupportedOsFailsClosed() {
        assertTrue(new UnsupportedDiscovery().listeningPorts(1234L).isEmpty());
    }
}
