/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import dev.jahaziel.plugin.Plugin;
import dev.jahaziel.plugin.PluginDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * Base for per-OS process-to-port discovery. Reads operating-system data without changing process
 * or network state. Unsupported hosts and parse failures yield an empty result, never an exception.
 */
public abstract class PortDiscoveryPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger(PortDiscoveryPlugin.class);
    protected static final int MAX_LINE_LENGTH = 1024;
    private static final long COMMAND_TIMEOUT_SECONDS = 5;

    protected PortDiscoveryPlugin(PluginDescriptor descriptor) {
        super(descriptor);
    }

    public final String name() {
        return descriptor().name();
    }

    /** Ports owned by one server-managed process id. */
    public abstract List<Integer> listeningPorts(long pid);

    /** Sorted union of the ports owned by the supplied process ids. */
    public final List<Integer> listeningPorts(Collection<Long> pids) {
        Set<Integer> union = new TreeSet<Integer>();
        if (pids == null) {
            return new ArrayList<Integer>(union);
        }
        for (Long pid : pids) {
            if (pid == null) {
                continue;
            }
            union.addAll(listeningPorts(pid.longValue()));
        }
        return new ArrayList<Integer>(union);
    }

    /** Upper bound on lines inspected by parsers and command readers. */
    protected int maxLines() {
        return 4096;
    }

    /** True when this discovery strategy applies to the current host. */
    protected boolean supportedOs() {
        return true;
    }

    /**
     * Reads one line while retaining at most {@code limit} characters for it. Overlong lines are
     * consumed through the newline and returned as empty so callers skip them.
     */
    protected static String readBoundedLine(BufferedReader reader, int limit) throws IOException {
        StringBuilder line = new StringBuilder();
        boolean overlong = false;
        while (true) {
            int ch = reader.read();
            if (ch == -1) {
                if (line.length() == 0) {
                    return null;
                }
                break;
            }
            if (ch == '\n') {
                break;
            }
            if (ch == '\r') {
                continue;
            }
            if (line.length() < limit) {
                line.append((char) ch);
            } else {
                overlong = true;
            }
        }
        if (overlong) {
            return "";
        }
        return line.toString();
    }

    /** Extracts the port from an address of the form {@code host:port}, or -1 when malformed. */
    protected static int parsePort(String address, int radix) {
        if (address == null) {
            return -1;
        }
        int colon = address.lastIndexOf(':');
        if (colon < 0 || colon + 1 >= address.length()) {
            return -1;
        }
        try {
            int port = Integer.parseInt(address.substring(colon + 1), radix);
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException malformed) {
            return -1;
        }
        return -1;
    }

    /** Parses a positive process id, or returns -1 for malformed values. */
    protected static long parsePid(String value) {
        try {
            long pid = Long.parseLong(value);
            return pid >= 1 ? pid : -1;
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    /** Converts grouped sets into immutable-by-convention lists for callers. */
    protected static Map<Long, List<Integer>> toLists(Map<Long, Set<Integer>> grouped) {
        Map<Long, List<Integer>> result = new HashMap<Long, List<Integer>>();
        for (Map.Entry<Long, Set<Integer>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), new ArrayList<Integer>(entry.getValue()));
        }
        return result;
    }

    /**
     * Runs a fixed command with a short timeout and returns at most {@link #maxLines()} output
     * lines. Callers pass literal commands only; configuration never reaches this method.
     */
    protected final List<String> runCommand(String[] command) {
        List<String> empty = new ArrayList<String>();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(), StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<String>();
            int limit = maxLines();
            int count = 0;
            String line;
            while (count < limit && (line = readBoundedLine(reader, MAX_LINE_LENGTH)) != null) {
                lines.add(line);
                count++;
            }
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.debug("{} timed out", command[0]);
                process.destroyForcibly();
                return empty;
            }
            return lines;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.debug("{} interrupted", command[0]);
            return empty;
        } catch (Exception failure) {
            log.debug("{} failed: {}", command[0], failure.toString());
            return empty;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
