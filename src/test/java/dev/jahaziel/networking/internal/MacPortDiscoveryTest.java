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

class MacPortDiscoveryTest {
    private static final List<String> LSOF =
            Arrays.asList(
                    "p1234",
                    "f3",
                    "n*:3000",
                    "n127.0.0.1:8080",
                    "f4",
                    "n[::1]:5000",
                    "p5678",
                    "f6",
                    "n*:3000",
                    "Xunexpected",
                    "pnotapid",
                    "n*:9000",
                    "nnot-an-address");

    private static final class UnsupportedDiscovery extends MacPortDiscovery {
        @Override
        protected boolean supportedOs() {
            return false;
        }
    }

    private static final class BoundedDiscovery extends MacPortDiscovery {
        @Override
        protected int maxLines() {
            return 4;
        }

        @Override
        protected boolean supportedOs() {
            return false;
        }
    }

    @Test
    void parsesPidAndPortFieldsFromRecordedLsof() {
        Map<Long, List<Integer>> parsed = new MacPortDiscovery().parseLsof(LSOF);
        assertEquals(
                Arrays.asList(Integer.valueOf(3000), Integer.valueOf(5000), Integer.valueOf(8080)),
                parsed.get(Long.valueOf(1234)));
        assertEquals(Arrays.asList(Integer.valueOf(3000)), parsed.get(Long.valueOf(5678)));
    }

    @Test
    void ignoresMalformedAndUnexpectedFields() {
        Map<Long, List<Integer>> parsed = new MacPortDiscovery().parseLsof(LSOF);
        assertFalse(parsed.containsKey(Long.valueOf(9000)));
        assertFalse(parsed.containsKey(Long.valueOf(-1)));
        assertFalse(parsed.containsKey(Long.valueOf(9999)));
        for (Map.Entry<Long, List<Integer>> entry : parsed.entrySet()) {
            assertFalse(entry.getValue().contains(Integer.valueOf(9000)));
        }
    }

    @Test
    void duplicatePortsForOnePidAreSuppressed() {
        List<String> recorded = new ArrayList<String>();
        recorded.add("p42");
        recorded.add("n*:7000");
        recorded.add("n*:7000");
        recorded.add("n*:7001");
        Map<Long, List<Integer>> parsed = new MacPortDiscovery().parseLsof(recorded);
        assertEquals(
                Arrays.asList(Integer.valueOf(7000), Integer.valueOf(7001)),
                parsed.get(Long.valueOf(42)));
    }

    @Test
    void boundedParsingOnlyInspectsConfiguredNumberOfLines() {
        Map<Long, List<Integer>> parsed = new BoundedDiscovery().parseLsof(LSOF);
        assertEquals(1, parsed.size());
        assertTrue(parsed.containsKey(Long.valueOf(1234)));
        assertFalse(parsed.containsKey(Long.valueOf(5678)));
    }

    @Test
    void emptyOutputYieldsNoPorts() {
        assertTrue(new MacPortDiscovery().parseLsof(new ArrayList<String>()).isEmpty());
    }

    @Test
    void unsupportedOsFailsClosed() {
        assertTrue(new UnsupportedDiscovery().listeningPorts(1234L).isEmpty());
    }
}
