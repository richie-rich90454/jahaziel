/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import dev.jahaziel.networking.api.PortCandidateService;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Discovers listening ports owned by live workspace task/terminal process trees.
 *
 * <p>Read-only: parses {@code /proc/net/tcp} and per-process socket descriptors. Never registers a
 * port, opens a gateway route, or executes a command. Candidate ports are suggestions only; a port
 * must be explicitly registered and preview URLs remain workspace-authorized. On non-Linux hosts it
 * returns no candidates.
 */
@Service
public final class PortCandidateServiceImpl implements PortCandidateService {
    private static final Logger log = LoggerFactory.getLogger(PortCandidateServiceImpl.class);
    private static final int MAX_CANDIDATES = 64;
    private static final String LISTEN = "0A";

    private final WorkspaceProcessRegistry registry;

    public PortCandidateServiceImpl(WorkspaceProcessRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public List<Candidate> candidates(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Path proc = Path.of("/proc");
        if (!Files.isDirectory(proc, LinkOption.NOFOLLOW_LINKS)) return List.of();

        Map<Integer, Set<Source>> byPort = new HashMap<>();
        Map<String, Integer> inodePort = listenSocketPorts(proc);
        if (inodePort.isEmpty()) return List.of();

        for (WorkspaceProcessRegistry.TrackedProcess tracked :
                registry.trackedProcessTree(
                        workspaceId,
                        Set.of(
                                WorkspaceProcessRegistry.Kind.TASK,
                                WorkspaceProcessRegistry.Kind.TERMINAL))) {
            Source source =
                    tracked.kind() == WorkspaceProcessRegistry.Kind.TASK
                            ? Source.TASK
                            : Source.TERMINAL;
            for (String inode : socketInodes(proc, tracked.processId())) {
                Integer port = inodePort.get(inode);
                if (port != null)
                    byPort.computeIfAbsent(port, ignored -> new LinkedHashSet<>()).add(source);
            }
        }

        return byPort.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(MAX_CANDIDATES)
                .map(entry -> new Candidate(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    private static Map<String, Integer> listenSocketPorts(Path proc) {
        Map<String, Integer> result = new HashMap<>();
        for (String table : List.of("tcp", "tcp6")) {
            Path path = proc.resolve("net").resolve(table);
            List<String> lines;
            try {
                lines = Files.readAllLines(path, StandardCharsets.US_ASCII);
            } catch (IOException failure) {
                log.debug("cannot read {}", path, failure);
                continue;
            }
            for (String line : lines) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 10 || !fields[3].equals(LISTEN)) continue;
                String local = fields[1];
                int colon = local.lastIndexOf(':');
                if (colon < 0 || colon + 1 >= local.length()) continue;
                try {
                    int port = Integer.parseUnsignedInt(local.substring(colon + 1), 16);
                    if (port >= 1 && port <= 65535) result.put(fields[9], port);
                } catch (NumberFormatException ignored) {
                    // Skip malformed kernel table rows.
                }
            }
        }
        return result;
    }

    private static Set<String> socketInodes(Path proc, long pid) {
        Path fdDir = proc.resolve(Long.toString(pid)).resolve("fd");
        Set<String> inodes = new LinkedHashSet<>();
        List<Path> fds;
        try (var stream = Files.list(fdDir)) {
            fds = stream.toList();
        } catch (IOException failure) {
            return inodes;
        }
        for (Path fd : fds) {
            try {
                String target = Files.readSymbolicLink(fd).toString();
                int open = target.indexOf("socket:[");
                if (open < 0) continue;
                int close = target.indexOf(']', open);
                if (close > open) inodes.add(target.substring(open + 8, close));
            } catch (IOException | UnsupportedOperationException ignored) {
                // Descriptor may vanish between listing and read.
            }
        }
        return inodes;
    }
}
