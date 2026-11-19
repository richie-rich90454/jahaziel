/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lists, diffs and restores individual file versions using the snapshot service. */
public final class FileHistoryService {
    private static final long MAX_FILE_BYTES = 1024 * 1024;

    private final SnapshotService snapshots;
    private final WorkspaceFiles files;

    public FileHistoryService(SnapshotService snapshots, WorkspaceFiles files) {
        if (snapshots == null || files == null) {
            throw new IllegalArgumentException("snapshots and files are required");
        }
        this.snapshots = snapshots;
        this.files = files;
    }

    /** Lists snapshot versions newest first, as returned by the snapshot service. */
    public List<FileVersion> versions(WorkspaceId workspaceId) {
        List<SnapshotMetadata> metadata = snapshots.list(workspaceId);
        List<FileVersion> versions = new ArrayList<FileVersion>();
        for (SnapshotMetadata snapshot : metadata) {
            versions.add(
                    new FileVersion(
                            snapshot.id().value().toString(),
                            snapshot.tag(),
                            snapshot.createdAt() == null ? "" : snapshot.createdAt().toString(),
                            snapshot.snapshotPath()));
        }
        return versions;
    }

    /** Compares two snapshots and reports added, modified and removed files. */
    public List<FileDiffEntry> diff(WorkspaceId workspaceId, String fromId, String toId) {
        SnapshotMetadata from = find(workspaceId, fromId);
        SnapshotMetadata to = find(workspaceId, toId);
        Map<String, byte[]> before = contents(from.snapshotPath());
        Map<String, byte[]> after = contents(to.snapshotPath());
        List<FileDiffEntry> changes = new ArrayList<FileDiffEntry>();
        List<String> paths = new ArrayList<String>();
        paths.addAll(before.keySet());
        for (String path : after.keySet()) {
            if (!paths.contains(path)) {
                paths.add(path);
            }
        }
        Collections.sort(
                paths,
                new Comparator<String>() {
                    @Override
                    public int compare(String left, String right) {
                        return left.compareTo(right);
                    }
                });
        for (String path : paths) {
            boolean wasThere = before.containsKey(path);
            boolean isThere = after.containsKey(path);
            if (wasThere && isThere && Arrays.equals(before.get(path), after.get(path))) {
                continue;
            }
            String change = FileDiffEntry.MODIFIED;
            if (!wasThere) {
                change = FileDiffEntry.ADDED;
            } else if (!isThere) {
                change = FileDiffEntry.REMOVED;
            }
            changes.add(new FileDiffEntry(path, change));
        }
        return changes;
    }

    /**
     * Restores one file from a snapshot through the workspace files API. The expectedVersion is
     * forwarded as an ETag so a concurrent edit causes a conflict instead of a silent overwrite.
     */
    public WorkspaceFiles.Document restoreFile(
            WorkspaceId workspaceId,
            String snapshotId,
            String relativePath,
            String expectedVersion) {
        SnapshotMetadata snapshot = find(workspaceId, snapshotId);
        Path source = PathGuard.confine(PathGuard.root(snapshot.snapshotPath()), relativePath);
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("file is not present in this snapshot");
        }
        String content;
        try {
            content = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read snapshot file", failure);
        }
        WorkspaceFiles.Operations operations = files.forWorkspace(workspaceId);
        return operations.save(relativePath, content, expectedVersion, expectedVersion == null);
    }

    private SnapshotMetadata find(WorkspaceId workspaceId, String snapshotId) {
        if (snapshotId == null) {
            throw new IllegalArgumentException("snapshotId is required");
        }
        UUID value;
        try {
            value = UUID.fromString(snapshotId);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid snapshotId: " + snapshotId, invalid);
        }
        List<SnapshotMetadata> metadata = snapshots.list(workspaceId);
        for (SnapshotMetadata snapshot : metadata) {
            if (snapshot.id().value().equals(value)) {
                return snapshot;
            }
        }
        throw new IllegalArgumentException("snapshot not found: " + snapshotId);
    }

    private static Map<String, byte[]> contents(Path root) {
        final Map<String, byte[]> result = new HashMap<String, byte[]>();
        final Path realRoot = PathGuard.root(root);
        try {
            Files.walkFileTree(
                    realRoot,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attributes) {
                            if (attributes.isRegularFile() && attributes.size() <= MAX_FILE_BYTES) {
                                try {
                                    result.put(
                                            realRoot.relativize(file).toString().replace('\\', '/'),
                                            Files.readAllBytes(file));
                                } catch (IOException unreadable) {
                                    // Skip files that cannot be read rather than failing the whole
                                    // diff.
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read snapshot", failure);
        }
        return result;
    }
}
