/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Clones get a fresh id, a copied subset, and no shared process state. */
final class WorkspaceCloneServiceTest {
    @Test
    void clonesOnlyTheRequestedSubset(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final WorkspaceId source = SafetyFixtures.workspace();
        SafetyFixtures.write(SafetyPaths.home(data, source).resolve("a.txt"), "a");
        SafetyFixtures.write(SafetyPaths.home(data, source).resolve("b.txt"), "b");

        final WorkspaceId created = new WorkspaceId(UUID.randomUUID());
        WorkspaceCloneService service =
                new WorkspaceCloneService(new FakeWorkspaces(created), data, "local");
        List<String> subset = new ArrayList<String>();
        subset.add("a.txt");
        WorkspaceCloneResult result =
                service.clone(SafetyFixtures.user(), source, "my-clone", subset);

        assertEquals(created.value().toString(), result.getId());
        assertEquals("my-clone", result.getName());
        assertEquals(1, result.getCopiedFiles());
        Path target = SafetyPaths.home(data, created).resolve("a.txt");
        assertTrue(Files.exists(target));
        assertEquals("a", SafetyFixtures.read(target));
        assertTrue(!Files.exists(SafetyPaths.home(data, created).resolve("b.txt")));
    }

    @Test
    void clonesFromTemplate(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        SafetyFixtures.write(SafetyPaths.templates(data).resolve("starter").resolve("t.txt"), "t");
        final WorkspaceId created = new WorkspaceId(UUID.randomUUID());
        WorkspaceCloneService service =
                new WorkspaceCloneService(new FakeWorkspaces(created), data, "local");
        WorkspaceCloneResult result =
                service.cloneFromTemplate(SafetyFixtures.user(), "starter", "from-template", null);
        assertEquals(1, result.getCopiedFiles());
        assertEquals("t", SafetyFixtures.read(SafetyPaths.home(data, created).resolve("t.txt")));
    }

    private static final class FakeWorkspaces implements WorkspaceService {
        private final WorkspaceId created;

        private FakeWorkspaces(WorkspaceId created) {
            this.created = created;
        }

        @Override
        public Workspace create(UserId owner, String name, int cpus, long memory, NodeId node) {
            return new Workspace(
                    created,
                    owner,
                    name,
                    WorkspaceQuota.defaults(),
                    new VirtualMachine(
                            new VirtualMachineId(UUID.randomUUID()),
                            created,
                            new VirtualMachineConfiguration(1, 512)));
        }

        @Override
        public Workspace get(WorkspaceId id) {
            return null;
        }

        @Override
        public WorkspaceView describe(WorkspaceId id) {
            return null;
        }

        @Override
        public List<WorkspaceView> describeByOwner(UserId owner) {
            return new ArrayList<WorkspaceView>();
        }

        @Override
        public List<WorkspaceView> describeAll() {
            return new ArrayList<WorkspaceView>();
        }

        @Override
        public void save(Workspace workspace) {}

        @Override
        public void delete(WorkspaceId id) {}
    }
}
