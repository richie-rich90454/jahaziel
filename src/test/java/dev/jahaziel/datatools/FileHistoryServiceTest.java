/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.workspace.api.WorkspaceFileConflictException;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.internal.WorkspaceFilesServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class FileHistoryServiceTest {
    @TempDir Path temporary;

    private WorkspaceId workspaceId;
    private SnapshotService snapshots;
    private WorkspaceFiles files;
    private FileHistoryService history;
    private SnapshotId firstId;
    private SnapshotId secondId;

    @BeforeEach
    void setUp() throws Exception {
        workspaceId = new WorkspaceId(UUID.randomUUID());
        snapshots = Mockito.mock(SnapshotService.class);
        files = new WorkspaceFilesServiceImpl(temporary.toString());
        files.forWorkspace(workspaceId).createDirectories(".");
        history = new FileHistoryService(snapshots, files);

        firstId = new SnapshotId(UUID.randomUUID());
        secondId = new SnapshotId(UUID.randomUUID());
        Path first = temporary.resolve("snapshots").resolve("first");
        Path second = temporary.resolve("snapshots").resolve("second");
        Files.createDirectories(first);
        Files.createDirectories(second);
        Files.write(first.resolve("note.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        Files.write(second.resolve("note.txt"), "v2".getBytes(StandardCharsets.UTF_8));
        Files.write(first.resolve("same.txt"), "same".getBytes(StandardCharsets.UTF_8));
        Files.write(second.resolve("same.txt"), "same".getBytes(StandardCharsets.UTF_8));
        Files.write(first.resolve("only-old.txt"), "old".getBytes(StandardCharsets.UTF_8));
        Files.write(second.resolve("only-new.txt"), "new".getBytes(StandardCharsets.UTF_8));

        List<SnapshotMetadata> metadata = new ArrayList<SnapshotMetadata>();
        metadata.add(metadata(firstId, "first", first));
        metadata.add(metadata(secondId, "second", second));
        Mockito.when(snapshots.list(workspaceId)).thenReturn(metadata);
    }

    @Test
    void listsVersionsAndDiffsTwoSnapshots() {
        List<FileVersion> versions = history.versions(workspaceId);
        assertEquals(2, versions.size());
        assertEquals("first", versions.get(0).tag());
        assertEquals(firstId.value().toString(), versions.get(0).id());

        List<FileDiffEntry> changes =
                history.diff(workspaceId, firstId.value().toString(), secondId.value().toString());
        assertEquals(3, changes.size());
        assertEquals(FileDiffEntry.MODIFIED, findChange(changes, "note.txt"));
        assertEquals(FileDiffEntry.REMOVED, findChange(changes, "only-old.txt"));
        assertEquals(FileDiffEntry.ADDED, findChange(changes, "only-new.txt"));
    }

    @Test
    void restoresSingleFileWithEtagConflictHandling() {
        WorkspaceFiles.Document created =
                history.restoreFile(workspaceId, secondId.value().toString(), "note.txt", null);
        assertEquals("v2", files.forWorkspace(workspaceId).read("note.txt").content());

        final String stale = "stale-version";
        assertThrows(
                WorkspaceFileConflictException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        history.restoreFile(
                                workspaceId, secondId.value().toString(), "note.txt", stale);
                    }
                });

        WorkspaceFiles.Document updated =
                history.restoreFile(
                        workspaceId, firstId.value().toString(), "note.txt", created.version());
        assertTrue(!updated.version().equals(created.version()));
        assertEquals("v1", files.forWorkspace(workspaceId).read("note.txt").content());
    }

    @Test
    void rejectsEscapingRestorePaths() {
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        history.restoreFile(
                                workspaceId, secondId.value().toString(), "../outside.txt", null);
                    }
                });
    }

    private static String findChange(List<FileDiffEntry> changes, String path) {
        for (FileDiffEntry change : changes) {
            if (change.path().equals(path)) {
                return change.change();
            }
        }
        return "";
    }

    private SnapshotMetadata metadata(SnapshotId id, String tag, Path path) {
        return new SnapshotMetadata(
                id,
                workspaceId,
                tag,
                SnapshotKind.MANUAL,
                Instant.now(),
                "tester",
                path,
                0,
                0,
                Optional.empty(),
                "");
    }
}
