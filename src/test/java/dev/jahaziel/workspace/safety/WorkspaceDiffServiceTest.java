/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.diff.WorkspaceDiff;
import dev.jahaziel.snapshot.diff.WorkspaceDiffService;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Diff uses the existing snapshot paths and reports added, removed, and modified files. */
public final class WorkspaceDiffServiceTest {
    @Test
    void comparesSnapshotWithCurrentTree(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final WorkspaceId workspace = SafetyFixtures.workspace();
        final Path snapshot = temp.resolve("snapshot");
        SafetyFixtures.write(snapshot.resolve("same.txt"), "same");
        SafetyFixtures.write(snapshot.resolve("changed.txt"), "before");
        SafetyFixtures.write(snapshot.resolve("removed.txt"), "gone");

        Path home = SafetyPaths.home(data, workspace);
        SafetyFixtures.write(home.resolve("same.txt"), "same");
        SafetyFixtures.write(home.resolve("changed.txt"), "after");
        SafetyFixtures.write(home.resolve("added.txt"), "new");

        SnapshotId id = new SnapshotId(UUID.randomUUID());
        SnapshotMetadata metadata =
                new SnapshotMetadata(
                        id,
                        workspace,
                        "tag",
                        SnapshotKind.MANUAL,
                        Instant.now(),
                        "user",
                        snapshot,
                        0,
                        0,
                        Optional.empty(),
                        "pending");
        WorkspaceDiffService service = new WorkspaceDiffService(new FakeSnapshots(metadata), data);
        WorkspaceDiff diff = service.diff(workspace, id.value().toString());
        assertEquals(1, diff.getAdded().size());
        assertEquals("added.txt", diff.getAdded().get(0));
        assertEquals(1, diff.getRemoved().size());
        assertEquals("removed.txt", diff.getRemoved().get(0));
        assertEquals(1, diff.getModified().size());
        assertEquals("changed.txt", diff.getModified().get(0));
        assertTrue(!diff.getAdded().contains("same.txt"));
    }

    private static final class FakeSnapshots extends SnapshotService {
        private final SnapshotMetadata metadata;

        private FakeSnapshots(SnapshotMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public SnapshotMetadata create(WorkspaceId id, String tag, SnapshotKind kind, String by) {
            return metadata;
        }

        @Override
        public List<SnapshotMetadata> list(WorkspaceId id) {
            List<SnapshotMetadata> result = new ArrayList<SnapshotMetadata>();
            result.add(metadata);
            return result;
        }

        @Override
        public void restore(WorkspaceId id, SnapshotId snapshotId) {}

        @Override
        public void delete(WorkspaceId id, SnapshotId snapshotId) {}
    }
}
