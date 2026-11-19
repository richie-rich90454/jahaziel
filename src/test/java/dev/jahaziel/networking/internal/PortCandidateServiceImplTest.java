/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.networking.api.PortCandidateService.Candidate;
import dev.jahaziel.networking.api.PortCandidateService.Source;
import dev.jahaziel.plugin.PluginContext;
import dev.jahaziel.plugin.PluginDescriptor;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry.Kind;
import dev.jahaziel.workspace.api.WorkspaceProcessRegistry.TrackedProcess;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class PortCandidateServiceImplTest {
    private static final class FakePlugin extends PortDiscoveryPlugin {
        private final Map<Long, List<Integer>> byPid = new HashMap<Long, List<Integer>>();

        FakePlugin() {
            super(
                    new PluginDescriptor(
                            "fake-port-discovery",
                            "1.0",
                            new ArrayList<String>(),
                            new ArrayList<String>()));
        }

        void set(long pid, Integer... ports) {
            List<Integer> values = new ArrayList<Integer>();
            for (Integer port : ports) {
                values.add(port);
            }
            byPid.put(Long.valueOf(pid), values);
        }

        @Override
        public void initialize(PluginContext context) {}

        @Override
        public void shutdown() {}

        @Override
        public List<Integer> listeningPorts(long pid) {
            List<Integer> ports = byPid.get(Long.valueOf(pid));
            if (ports == null) {
                return new ArrayList<Integer>();
            }
            return new ArrayList<Integer>(ports);
        }
    }

    private WorkspaceProcessRegistry registry;
    private FakePlugin plugin;
    private PortCandidateServiceImpl service;
    private WorkspaceId workspaceId;

    @BeforeEach
    void setUp() {
        registry = mock(WorkspaceProcessRegistry.class);
        plugin = new FakePlugin();
        service = new PortCandidateServiceImpl(registry, new PortDiscoveries(plugin));
        workspaceId = new WorkspaceId(UUID.randomUUID());
    }

    private void track(TrackedProcess... processes) {
        when(registry.trackedProcessTree(any(), any())).thenReturn(Arrays.asList(processes));
    }

    private static Candidate find(List<Candidate> candidates, int port) {
        for (Candidate candidate : candidates) {
            if (candidate.port() == port) {
                return candidate;
            }
        }
        return null;
    }

    @Test
    void onlyTrackedTaskAndTerminalTreesContributeCandidates() {
        plugin.set(10L, Integer.valueOf(3000), Integer.valueOf(8080));
        plugin.set(20L, Integer.valueOf(8080), Integer.valueOf(5000));
        plugin.set(30L, Integer.valueOf(9999));
        track(
                new TrackedProcess(Kind.TASK, 10L),
                new TrackedProcess(Kind.TERMINAL, 20L),
                new TrackedProcess(Kind.SETUP, 30L));

        List<Candidate> candidates = service.candidates(workspaceId);

        assertEquals(3, candidates.size());
        assertEquals(Arrays.asList(Source.TASK), find(candidates, 3000).sources());
        assertEquals(Arrays.asList(Source.TERMINAL), find(candidates, 5000).sources());
        assertEquals(Arrays.asList(Source.TASK, Source.TERMINAL), find(candidates, 8080).sources());
        assertNull(find(candidates, 9999));
    }

    @Test
    void dedupesPortsAndSortsByPort() {
        plugin.set(10L, Integer.valueOf(9000), Integer.valueOf(8080), Integer.valueOf(9000));
        plugin.set(20L, Integer.valueOf(8080));
        track(new TrackedProcess(Kind.TASK, 10L), new TrackedProcess(Kind.TERMINAL, 20L));

        List<Candidate> candidates = service.candidates(workspaceId);

        assertEquals(2, candidates.size());
        assertEquals(8080, candidates.get(0).port());
        assertEquals(9000, candidates.get(1).port());
        assertEquals(1, find(candidates, 9000).sources().size());
    }

    @Test
    void capsCandidatesAtSixtyFour() {
        Integer[] ports = new Integer[70];
        for (int i = 0; i < ports.length; i++) {
            ports[i] = Integer.valueOf(i + 1);
        }
        plugin.set(10L, ports);
        track(new TrackedProcess(Kind.TASK, 10L));

        List<Candidate> candidates = service.candidates(workspaceId);

        assertEquals(64, candidates.size());
        assertEquals(1, candidates.get(0).port());
        assertEquals(64, candidates.get(63).port());
        assertNull(find(candidates, 65));
    }

    @Test
    void untrackedProcessesYieldNoCandidates() {
        plugin.set(10L, Integer.valueOf(8080));
        track();

        assertTrue(service.candidates(workspaceId).isEmpty());
    }

    @Test
    void nullWorkspaceIsRejected() {
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.candidates(null);
                    }
                });
    }
}
