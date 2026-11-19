/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Polls workspace file mtimes and hashes; no operating-system watcher and no external dependency. */
@Component
public final class FileWatchService {
    private final String storageRoot;

    public FileWatchService(@Value("${jahaziel.storage.root:data}") String storageRoot) {
        this.storageRoot = SafetyPaths.root(storageRoot).toString();
    }

    /** Returns changes observed since an epoch-millisecond timestamp and advances the baseline. */
    public synchronized List<WatchChange> changesSince(WorkspaceId workspace, long sinceMillis) {
        Path home = SafetyPaths.home(storageRoot, workspace);
        Map<String, Observed> current = observe(home);
        Map<String, Observed> baseline = load(workspace);
        List<WatchChange> changes = new ArrayList<WatchChange>();
        for (Map.Entry<String, Observed> entry : current.entrySet()) {
            Observed previous = baseline.get(entry.getKey());
            Observed now = entry.getValue();
            if (now.modifiedAt <= sinceMillis) {
                continue;
            }
            if (previous == null) {
                changes.add(new WatchChange(entry.getKey(), WatchChange.ADDED, now.modifiedAt));
            } else if (!previous.hash.equals(now.hash)) {
                changes.add(new WatchChange(entry.getKey(), WatchChange.MODIFIED, now.modifiedAt));
            }
        }
        for (Map.Entry<String, Observed> entry : baseline.entrySet()) {
            if (!current.containsKey(entry.getKey()) && entry.getValue().modifiedAt > sinceMillis) {
                changes.add(
                        new WatchChange(
                                entry.getKey(), WatchChange.DELETED, entry.getValue().modifiedAt));
            }
        }
        save(workspace, current);
        return changes;
    }

    private static Map<String, Observed> observe(Path root) {
        final Map<String, Observed> result = new HashMap<String, Observed>();
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
                            if (!directory.equals(root) && ignored(directory.getFileName().toString())) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                            if (!attributes.isRegularFile()) {
                                return FileVisitResult.CONTINUE;
                            }
                            String relative = root.relativize(file).toString().replace('\\', '/');
                            try {
                                result.put(
                                        relative,
                                        new Observed(
                                                attributes.lastModifiedTime().toMillis(),
                                                hash(Files.readAllBytes(file))));
                            } catch (IOException failure) {
                                throw new UncheckedIOException(failure);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot poll workspace files", failure);
        }
        return result;
    }

    private Map<String, Observed> load(WorkspaceId workspace) {
        Map<String, Observed> result = new HashMap<String, Observed>();
        Path file = SafetyPaths.watch(storageRoot, workspace).resolve("baseline.dat");
        String[] lines = SafetyPaths.readText(file).split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(" ", -1);
            if (parts.length < 3) {
                continue;
            }
            result.put(
                    SafetyPaths.decodeText(parts[0]),
                    new Observed(Long.parseLong(parts[1]), parts[2]));
        }
        return result;
    }

    private void save(WorkspaceId workspace, Map<String, Observed> observed) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Observed> entry : observed.entrySet()) {
            text.append(SafetyPaths.encodeText(entry.getKey()))
                    .append(' ')
                    .append(entry.getValue().modifiedAt)
                    .append(' ')
                    .append(entry.getValue().hash)
                    .append('\n');
        }
        SafetyPaths.writeText(
                SafetyPaths.watch(storageRoot, workspace).resolve("baseline.dat"), text.toString());
    }

    private static boolean ignored(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals(".git")
                || lower.equals(".cache")
                || lower.equals("node_modules")
                || lower.equals("target");
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static final class Observed {
        private final long modifiedAt;
        private final String hash;

        private Observed(long modifiedAt, String hash) {
            this.modifiedAt = modifiedAt;
            this.hash = hash;
        }
    }
}
