/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.diff;

import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Compares a workspace tree with an existing snapshot using the snapshot service paths. */
@Component
public final class WorkspaceDiffService {
    private final SnapshotService snapshots;
    private final String storageRoot;

    public WorkspaceDiffService(
            SnapshotService snapshots, @Value("${jahaziel.storage.root:data}") String storageRoot) {
        if (snapshots == null) {
            throw new IllegalArgumentException("snapshots is required");
        }
        this.snapshots = snapshots;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize().toString();
    }

    /** Returns the differences between a snapshot and the current workspace home. */
    public WorkspaceDiff diff(WorkspaceId workspace, String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId is required");
        }
        UUID identifier;
        try {
            identifier = UUID.fromString(snapshotId);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("snapshotId is invalid");
        }
        Path snapshotPath = null;
        List<SnapshotMetadata> found = snapshots.list(workspace);
        for (int index = 0; index < found.size(); index++) {
            SnapshotMetadata metadata = found.get(index);
            if (metadata.id().value().equals(identifier)) {
                snapshotPath = metadata.snapshotPath();
                break;
            }
        }
        if (snapshotPath == null) {
            throw new IllegalArgumentException("snapshot not found for this workspace");
        }
        Map<String, String> before = hashes(snapshotPath);
        Map<String, String> after =
                hashes(
                        Path.of(storageRoot)
                                .resolve("workspaces")
                                .resolve(workspace.value().toString())
                                .resolve("home"));
        WorkspaceDiff result = new WorkspaceDiff();
        for (Map.Entry<String, String> entry : after.entrySet()) {
            String path = entry.getKey();
            if (!before.containsKey(path)) {
                result.getAdded().add(path);
            } else if (!before.get(path).equals(entry.getValue())) {
                result.getModified().add(path);
            }
        }
        for (Map.Entry<String, String> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                result.getRemoved().add(entry.getKey());
            }
        }
        return result;
    }

    private static Map<String, String> hashes(Path root) {
        final Map<String, String> result = new HashMap<String, String>();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return result;
        }
        try {
            Files.walkFileTree(
                    root,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path directory, BasicFileAttributes attributes) {
                            if (!directory.equals(root)
                                    && ignored(directory.getFileName().toString())) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attributes) {
                            if (attributes.isRegularFile()) {
                                String relative =
                                        root.relativize(file).toString().replace('\\', '/');
                                try {
                                    result.put(relative, hash(Files.readAllBytes(file)));
                                } catch (IOException failure) {
                                    throw new UncheckedIOException(failure);
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot hash workspace tree", failure);
        }
        return result;
    }

    private static boolean ignored(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals(".git")
                || lower.equals(".cache")
                || lower.equals("node_modules")
                || lower.equals("target")
                || lower.equals("dist")
                || lower.equals("build");
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
