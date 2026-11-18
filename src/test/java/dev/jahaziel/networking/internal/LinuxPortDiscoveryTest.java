/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LinuxPortDiscoveryTest {
    private static final String ROW_8080 =
            "  0: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000 1000 0 1111 1";
    private static final String ROW_3000 =
            "  1: 0100007F:0BB8 00000000:0000 0A 00000000:00000000 00:00000000 00000000 1000 0 2222 1";
    private static final String ROW_10000_V6 =
            "  2: 0000000000000000:2710 00000000000000000000000000000000:0000 0A"
                    + " 00000000:00000000 00:00000000 00000000 1000 0 3333 1";
    private static final String ROW_ESTABLISHED =
            "  3: 0100007F:1F90 0100007F:1234 01 00000000:00000000 00:00000000 00000000 1000 0 4444 1";
    private static final String ROW_BAD_PORT =
            "  4: 0100007F:ZZZZ 00000000:0000 0A 00000000:00000000 00:00000000 00000000 1000 0 5555 1";
    private static final String ROW_SHORT = "  5: 0100007F:1F90 00000000:0000 0A";

    private static final class BoundedDiscovery extends LinuxPortDiscovery {
        private final int limit;

        BoundedDiscovery(int limit) {
            this.limit = limit;
        }

        @Override
        protected int maxLines() {
            return limit;
        }

        @Override
        protected boolean supportedOs() {
            return false;
        }
    }

    private static BufferedReader reader(String text) {
        return new BufferedReader(new StringReader(text));
    }

    @Test
    void parsesListenRowsFromRecordedProcText() {
        String table = ROW_8080 + "\n" + ROW_3000 + "\n" + ROW_10000_V6 + "\n";
        Map<String, Integer> parsed = new LinuxPortDiscovery().parseListenTable(reader(table));
        assertEquals(3, parsed.size());
        assertEquals(Integer.valueOf(8080), parsed.get("1111"));
        assertEquals(Integer.valueOf(3000), parsed.get("2222"));
        assertEquals(Integer.valueOf(10000), parsed.get("3333"));
    }

    @Test
    void skipsMalformedAndNonListenRows() {
        String table = ROW_8080 + "\n" + ROW_ESTABLISHED + "\n" + ROW_BAD_PORT + "\n"
                + ROW_SHORT + "\n";
        Map<String, Integer> parsed = new LinuxPortDiscovery().parseListenTable(reader(table));
        assertEquals(1, parsed.size());
        assertTrue(parsed.containsKey("1111"));
        assertFalse(parsed.containsKey("4444"));
        assertFalse(parsed.containsKey("5555"));
    }

    @Test
    void extractsSocketInodesFromLinkTargets() {
        List<String> targets = Arrays.asList(
                "socket:[1111]", "pipe:[5]", "socket:[2222]", "/dev/null", "socket:[3333]");
        Set<String> expected = new HashSet<String>();
        expected.add("1111");
        expected.add("2222");
        expected.add("3333");
        assertEquals(expected, new LinuxPortDiscovery().parseSocketInodes(targets));
    }

    @Test
    void mapsInodesToSortedPorts() {
        Map<String, Integer> sockets = new LinuxPortDiscovery().parseListenTable(
                reader(ROW_8080 + "\n" + ROW_10000_V6 + "\n"));
        Set<String> inodes = new HashSet<String>();
        inodes.add("3333");
        inodes.add("1111");
        inodes.add("9999");
        List<Integer> ports = new LinuxPortDiscovery().portsForInodes(sockets, inodes);
        assertEquals(
                Arrays.asList(Integer.valueOf(8080), Integer.valueOf(10000)), ports);
    }

    @Test
    void boundedParsingOnlyInspectsConfiguredNumberOfLines() {
        StringBuilder table = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            table.append("  0: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000"
                    + " 00000000 1000 0 ");
            table.append(1000 + i);
            table.append(" 1\n");
        }
        Map<String, Integer> parsed = new BoundedDiscovery(3).parseListenTable(reader(table.toString()));
        assertEquals(3, parsed.size());
        assertTrue(parsed.containsKey("1000"));
        assertTrue(parsed.containsKey("1002"));
        assertFalse(parsed.containsKey("1003"));
    }

    @Test
    void overlongLinesAreSkippedWithoutBufferingThem() {
        StringBuilder overlong = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            overlong.append('x');
        }
        String table = overlong.toString() + "\n" + ROW_8080 + "\n";
        Map<String, Integer> parsed = new LinuxPortDiscovery().parseListenTable(reader(table));
        assertEquals(1, parsed.size());
        assertEquals(Integer.valueOf(8080), parsed.get("1111"));
    }

    @Test
    void unsupportedOsFailsClosed() {
        assertTrue(new BoundedDiscovery(3).listeningPorts(42L).isEmpty());
    }

    @Test
    void emptyTargetsYieldNoInodes() {
        assertTrue(new LinuxPortDiscovery().parseSocketInodes(new ArrayList<String>()).isEmpty());
    }
}
