/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import dev.jahaziel.networking.api.PortCandidateService;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Discovers listening ports owned by live workspace task/terminal process trees.
 *
 * <p>Read-only and cross-platform: delegates OS-specific discovery to {@link PortDiscoveries} and
 * keeps only ports held by processes the workspace registry tracks. Never registers a port, opens a
 * gateway route, or runs a command from user input. Candidate ports are suggestions only; a port
 * must be explicitly registered and preview URLs remain workspace-authorized.
 */
@Service
public final class PortCandidateServiceImpl implements PortCandidateService {
    private static final int MAX_CANDIDATES = 64;

    private final WorkspaceProcessRegistry registry;
    private final PortDiscoveries discoveries;

    public PortCandidateServiceImpl(WorkspaceProcessRegistry registry) {
        this(registry, new PortDiscoveries());
    }

    PortCandidateServiceImpl(WorkspaceProcessRegistry registry, PortDiscoveries discoveries) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.discoveries = Objects.requireNonNull(discoveries, "discoveries");
    }

    @Override
    public List<Candidate> candidates(WorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Map<Integer, Set<Source>> byPort = new TreeMap<Integer, Set<Source>>();

        Set<WorkspaceProcessRegistry.Kind> kinds = new HashSet<WorkspaceProcessRegistry.Kind>();
        kinds.add(WorkspaceProcessRegistry.Kind.TASK);
        kinds.add(WorkspaceProcessRegistry.Kind.TERMINAL);

        List<Long> taskPids = new ArrayList<Long>();
        List<Long> terminalPids = new ArrayList<Long>();
        for (WorkspaceProcessRegistry.TrackedProcess tracked :
                registry.trackedProcessTree(workspaceId, kinds)) {
            if (tracked.kind() == WorkspaceProcessRegistry.Kind.TASK) {
                taskPids.add(Long.valueOf(tracked.processId()));
            } else if (tracked.kind() == WorkspaceProcessRegistry.Kind.TERMINAL) {
                terminalPids.add(Long.valueOf(tracked.processId()));
            }
        }
        for (Integer port : discoveries.candidates(taskPids)) {
            addSource(byPort, port, Source.TASK);
        }
        for (Integer port : discoveries.candidates(terminalPids)) {
            addSource(byPort, port, Source.TERMINAL);
        }

        List<Candidate> result = new ArrayList<Candidate>();
        for (Map.Entry<Integer, Set<Source>> entry : byPort.entrySet()) {
            if (result.size() >= MAX_CANDIDATES) break;
            result.add(
                    new Candidate(
                            entry.getKey().intValue(), new ArrayList<Source>(entry.getValue())));
        }
        return result;
    }

    private static void addSource(Map<Integer, Set<Source>> byPort, Integer port, Source source) {
        Set<Source> sources = byPort.get(port);
        if (sources == null) {
            sources = new LinkedHashSet<Source>();
            byPort.put(port, sources);
        }
        sources.add(source);
    }
}
