/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Windows discovery from a fixed {@code netstat -ano -p TCP} invocation. Only LISTENING rows are
 * considered, for both IPv4 and IPv6 local addresses; duplicate rows collapse into one port.
 */
public class WindowsPortDiscovery extends PortDiscoveryPlugin {
    private static final Logger log = LoggerFactory.getLogger(WindowsPortDiscovery.class);
    private static final String[] COMMAND = new String[] {"netstat", "-ano", "-p", "TCP"};
    private static final String PROTOCOL = "TCP";
    private static final String STATE = "LISTENING";

    public WindowsPortDiscovery() {
        super(new PluginDescriptor(
                "windows-port-discovery", "1.0", new ArrayList<String>(), new ArrayList<String>()));
    }

    @Override
    public void initialize(PluginContext context) {}

    @Override
    public void shutdown() {}

    @Override
    protected boolean supportedOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Override
    public List<Integer> listeningPorts(long pid) {
        List<Integer> empty = new ArrayList<Integer>();
        if (!supportedOs() || pid < 1) {
            return empty;
        }
        try {
            Map<Long, List<Integer>> parsed = parseNetstat(runCommand(COMMAND));
            List<Integer> ports = parsed.get(Long.valueOf(pid));
            if (ports == null) {
                return empty;
            }
            return new ArrayList<Integer>(new TreeSet<Integer>(ports));
        } catch (Exception failure) {
            log.debug("Windows port discovery failed: {}", failure.toString());
            return empty;
        }
    }

    /** Parses recorded or live {@code netstat -ano -p TCP} lines into a pid-to-ports mapping. */
    protected Map<Long, List<Integer>> parseNetstat(List<String> lines) {
        Map<Long, Set<Integer>> byPid = new HashMap<Long, Set<Integer>>();
        int limit = Math.min(lines.size(), maxLines());
        for (int i = 0; i < limit; i++) {
            String line = lines.get(i);
            if (line == null) {
                continue;
            }
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 5) {
                continue;
            }
            if (!PROTOCOL.equalsIgnoreCase(fields[0]) || !STATE.equalsIgnoreCase(fields[3])) {
                continue;
            }
            int port = parsePort(fields[1], 10);
            if (port < 1) {
                continue;
            }
            long pid = parsePid(fields[4]);
            if (pid < 1) {
                continue;
            }
            Long key = Long.valueOf(pid);
            Set<Integer> ports = byPid.get(key);
            if (ports == null) {
                ports = new TreeSet<Integer>();
                byPid.put(key, ports);
            }
            ports.add(Integer.valueOf(port));
        }
        return toLists(byPid);
    }
}
