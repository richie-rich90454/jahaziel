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
 * macOS discovery from a fixed {@code lsof} invocation. Only the pid and name fields of the
 * machine-readable output are interpreted; every other field and all configuration are ignored.
 */
public class MacPortDiscovery extends PortDiscoveryPlugin {
    private static final Logger log = LoggerFactory.getLogger(MacPortDiscovery.class);
    private static final String[] COMMAND =
            new String[] {"lsof", "-nP", "-iTCP", "-sTCP:LISTEN", "-Fpfn"};

    public MacPortDiscovery() {
        super(new PluginDescriptor(
                "mac-port-discovery", "1.0", new ArrayList<String>(), new ArrayList<String>()));
    }

    @Override
    public void initialize(PluginContext context) {}

    @Override
    public void shutdown() {}

    @Override
    protected boolean supportedOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    @Override
    public List<Integer> listeningPorts(long pid) {
        List<Integer> empty = new ArrayList<Integer>();
        if (!supportedOs() || pid < 1) {
            return empty;
        }
        try {
            Map<Long, List<Integer>> parsed = parseLsof(runCommand(COMMAND));
            List<Integer> ports = parsed.get(Long.valueOf(pid));
            if (ports == null) {
                return empty;
            }
            return new ArrayList<Integer>(new TreeSet<Integer>(ports));
        } catch (Exception failure) {
            log.debug("macOS port discovery failed: {}", failure.toString());
            return empty;
        }
    }

    /** Parses recorded or live {@code lsof -Fpfn} lines into a pid-to-ports mapping. */
    protected Map<Long, List<Integer>> parseLsof(List<String> lines) {
        Map<Long, Set<Integer>> byPid = new HashMap<Long, Set<Integer>>();
        long currentPid = -1;
        int limit = Math.min(lines.size(), maxLines());
        for (int i = 0; i < limit; i++) {
            String line = lines.get(i);
            if (line == null || line.length() < 2) {
                continue;
            }
            char field = line.charAt(0);
            String value = line.substring(1);
            if (field == 'p') {
                currentPid = parsePid(value);
            } else if (field == 'n') {
                if (currentPid < 1) {
                    continue;
                }
                int port = parsePort(value, 10);
                if (port < 1) {
                    continue;
                }
                Long key = Long.valueOf(currentPid);
                Set<Integer> ports = byPid.get(key);
                if (ports == null) {
                    ports = new TreeSet<Integer>();
                    byPid.put(key, ports);
                }
                ports.add(Integer.valueOf(port));
            }
        }
        return toLists(byPid);
    }
}
