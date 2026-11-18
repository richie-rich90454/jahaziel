/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Linux discovery from the read-only {@code /proc} filesystem. Socket tables are streamed one line
 * at a time; per-process descriptor links are read without buffering whole files.
 */
public class LinuxPortDiscovery extends PortDiscoveryPlugin {
    private static final Logger log = LoggerFactory.getLogger(LinuxPortDiscovery.class);
    private static final String LISTEN = "0A";
    private static final int MAX_FDS = 4096;
    private static final String SOCKET_PREFIX = "socket:[";

    public LinuxPortDiscovery() {
        super(new PluginDescriptor(
                "linux-port-discovery", "1.0", new ArrayList<String>(), new ArrayList<String>()));
    }

    @Override
    public void initialize(PluginContext context) {}

    @Override
    public void shutdown() {}

    @Override
    protected boolean supportedOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")
                && Files.isDirectory(Paths.get("/proc"), LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    protected int maxLines() {
        return 65536;
    }

    @Override
    public List<Integer> listeningPorts(long pid) {
        List<Integer> empty = new ArrayList<Integer>();
        if (!supportedOs() || pid < 1) {
            return empty;
        }
        try {
            Map<String, Integer> inodePort = listenSocketPorts();
            if (inodePort.isEmpty()) {
                return empty;
            }
            Set<String> inodes = socketInodes(Paths.get("/proc", Long.toString(pid), "fd"));
            return portsForInodes(inodePort, inodes);
        } catch (Exception failure) {
            log.debug("Linux port discovery failed: {}", failure.toString());
            return empty;
        }
    }

    private Map<String, Integer> listenSocketPorts() {
        Map<String, Integer> result = new HashMap<String, Integer>();
        readTable(Paths.get("/proc", "net", "tcp"), result);
        readTable(Paths.get("/proc", "net", "tcp6"), result);
        return result;
    }

    private void readTable(Path path, Map<String, Integer> result) {
        BufferedReader reader = null;
        try {
            reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII);
            result.putAll(parseListenTable(reader));
        } catch (IOException failure) {
            log.debug("cannot read {}: {}", path, failure.toString());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // Nothing useful to do while closing a read-only handle.
                }
            }
        }
    }

    /** Parses inode-to-port mappings out of one recorded or live {@code /proc/net/tcp*} table. */
    protected Map<String, Integer> parseListenTable(BufferedReader reader) {
        Map<String, Integer> result = new HashMap<String, Integer>();
        int limit = maxLines();
        int count = 0;
        try {
            String line;
            while (count < limit && (line = readBoundedLine(reader, MAX_LINE_LENGTH)) != null) {
                count++;
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 10 || !LISTEN.equals(fields[3])) {
                    continue;
                }
                int port = parsePort(fields[1], 16);
                if (port < 1) {
                    continue;
                }
                result.put(fields[9], Integer.valueOf(port));
            }
        } catch (IOException failure) {
            log.debug("cannot parse Linux socket table: {}", failure.toString());
        }
        return result;
    }

    /** Extracts socket inodes from recorded or live {@code /proc/<pid>/fd} link targets. */
    protected Set<String> parseSocketInodes(List<String> targets) {
        Set<String> inodes = new LinkedHashSet<String>();
        int limit = Math.min(targets.size(), maxLines());
        for (int i = 0; i < limit; i++) {
            String target = targets.get(i);
            if (target == null) {
                continue;
            }
            int open = target.indexOf(SOCKET_PREFIX);
            if (open < 0) {
                continue;
            }
            int close = target.indexOf(']', open);
            if (close > open) {
                inodes.add(target.substring(open + SOCKET_PREFIX.length(), close));
            }
        }
        return inodes;
    }

    /** Maps a process's socket inodes to the sorted set of listening ports they own. */
    protected List<Integer> portsForInodes(Map<String, Integer> inodePort, Set<String> inodes) {
        Set<Integer> ports = new TreeSet<Integer>();
        for (String inode : inodes) {
            Integer port = inodePort.get(inode);
            if (port != null) {
                ports.add(port);
            }
        }
        return new ArrayList<Integer>(ports);
    }

    private Set<String> socketInodes(Path fdDir) {
        List<String> targets = new ArrayList<String>();
        DirectoryStream<Path> stream = null;
        try {
            stream = Files.newDirectoryStream(fdDir);
            int count = 0;
            for (Path fd : stream) {
                if (count >= MAX_FDS) {
                    break;
                }
                count++;
                try {
                    targets.add(Files.readSymbolicLink(fd).toString());
                } catch (IOException | UnsupportedOperationException vanished) {
                    // Descriptor may vanish between listing and read.
                }
            }
        } catch (IOException failure) {
            log.debug("cannot list {}: {}", fdDir, failure.toString());
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Nothing useful to do while closing a read-only handle.
                }
            }
        }
        return parseSocketInodes(targets);
    }
}
