/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.api.ApiFailureException;
import dev.jahaziel.api.WorkspaceSafetyController;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.node.domain.NodeId;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.diff.WorkspaceDiffService;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import dev.jahaziel.workspace.internal.WorkspaceFilesServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Controller checks authorization and exposes the contracted safety endpoints. */
final class WorkspaceSafetyControllerTest {
    @Test
    void controllerIsAnnotatedRestController() {
        assertTrue(WorkspaceSafetyController.class.isAnnotationPresent(RestController.class));
        RequestMapping mapping =
                WorkspaceSafetyController.class.getAnnotation(RequestMapping.class);
        assertTrue(mapping != null && mapping.value().length == 1);
        assertEquals("/api/v1/workspaces/{id}", mapping.value()[0]);
    }

    @Test
    void undoAndRedoFlowThroughController(@TempDir Path temp) {
        final Fixture fixture = new Fixture(temp, new SafetyFixtures.AllowAll());
        final Map<String, Object> upload = new HashMap<String, Object>();
        upload.put("path", "a.txt");
        upload.put("content", "hello");
        ResponseEntity<?> response =
                fixture.controller.upload(
                        SafetyFixtures.workspace().value(), SafetyFixtures.principal(), upload);
        assertTrue(response.getHeaders().getETag() != null);

        assertEquals(
                1,
                fixture.controller
                        .history(SafetyFixtures.workspace().value(), SafetyFixtures.principal())
                        .size());
        assertEquals(
                1,
                fixture.controller
                        .undo(SafetyFixtures.workspace().value(), SafetyFixtures.principal())
                        .size());
        assertTrue(
                !Files.exists(
                        SafetyPaths.home(fixture.data, SafetyFixtures.workspace())
                                .resolve("a.txt")));
        assertEquals(
                1,
                fixture.controller
                        .redo(SafetyFixtures.workspace().value(), SafetyFixtures.principal())
                        .size());
        assertEquals(
                "hello",
                SafetyFixtures.read(
                        SafetyPaths.home(fixture.data, SafetyFixtures.workspace())
                                .resolve("a.txt")));
    }

    @Test
    void trashAndBulkFlowThroughController(@TempDir Path temp) {
        final Fixture fixture = new Fixture(temp, new SafetyFixtures.AllowAll());
        Path home = SafetyPaths.home(fixture.data, SafetyFixtures.workspace());
        SafetyFixtures.write(home.resolve("t.txt"), "t");
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("path", "t.txt");
        TrashEntry entry =
                fixture.controller.trashFile(
                        SafetyFixtures.workspace().value(), SafetyFixtures.principal(), body);
        assertEquals(
                1,
                fixture.controller
                        .trashList(SafetyFixtures.workspace().value(), SafetyFixtures.principal())
                        .size());

        Map<String, Object> restore = new HashMap<String, Object>();
        restore.put("action", "restore");
        restore.put("itemId", entry.getId());
        fixture.controller.trashFile(
                SafetyFixtures.workspace().value(), SafetyFixtures.principal(), restore);
        assertEquals("t", SafetyFixtures.read(home.resolve("t.txt")));

        Map<String, Object> bulk = new HashMap<String, Object>();
        bulk.put("operation", "delete");
        List<String> paths = new ArrayList<String>();
        paths.add("t.txt");
        bulk.put("paths", paths);
        assertEquals(
                1,
                fixture.controller
                        .bulk(SafetyFixtures.workspace().value(), SafetyFixtures.principal(), bulk)
                        .getApplied());
    }

    @Test
    void deniedCallsProduceApiFailure(@TempDir Path temp) {
        final Fixture fixture = new Fixture(temp, new SafetyFixtures.DenyAll());
        assertThrows(
                ApiFailureException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        fixture.controller.history(
                                SafetyFixtures.workspace().value(), SafetyFixtures.principal());
                    }
                });
    }

    private static final class Fixture {
        private final String data;
        private final WorkspaceSafetyController controller;

        private Fixture(Path temp, AuthorizationService auth) {
            this.data = temp.resolve("data").toString();
            OperationHistory history = new OperationHistory(data);
            TrashService trash = new TrashService(data, 60000L, 1000000L);
            WorkspaceDiffService diff = new WorkspaceDiffService(new EmptySnapshots(), data);
            WorkspaceCloneService clone =
                    new WorkspaceCloneService(new MinimalWorkspaces(), data, "local");
            FileWatchService watch = new FileWatchService(data);
            BulkFileService bulk =
                    new BulkFileService(new WorkspaceFilesServiceImpl(data), history, data);
            UploadService upload =
                    new UploadService(new WorkspaceFilesServiceImpl(data), history, data);
            this.controller =
                    new WorkspaceSafetyController(
                            history, trash, diff, clone, watch, bulk, upload, auth);
        }
    }

    private static final class EmptySnapshots implements SnapshotService {
        @Override
        public SnapshotMetadata create(WorkspaceId id, String tag, SnapshotKind kind, String by) {
            return null;
        }

        @Override
        public List<SnapshotMetadata> list(WorkspaceId id) {
            return new ArrayList<SnapshotMetadata>();
        }

        @Override
        public void restore(WorkspaceId id, SnapshotId snapshotId) {}

        @Override
        public void delete(WorkspaceId id, SnapshotId snapshotId) {}
    }

    private static final class MinimalWorkspaces implements WorkspaceService {
        @Override
        public Workspace create(UserId owner, String name, int cpus, long memory, NodeId node) {
            WorkspaceId id = new WorkspaceId(UUID.randomUUID());
            return new Workspace(
                    id,
                    owner,
                    name,
                    WorkspaceQuota.defaults(),
                    new VirtualMachine(
                            new VirtualMachineId(UUID.randomUUID()),
                            id,
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
