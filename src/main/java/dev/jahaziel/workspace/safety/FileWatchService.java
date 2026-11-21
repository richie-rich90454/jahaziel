/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import dev.jahaziel.workspace.domain.WorkspaceId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Polls workspace file mtimes and hashes; no operating-system watcher and no external dependency.
 */
@Component
public final class FileWatchService {
    private static final int MAX_HASH_BYTES = 1024 * 1024;

    private final String storageRoot;
    private final ConcurrentMap<WorkspaceId, Object> locks = new ConcurrentHashMap<>();

    public FileWatchService(@Value("${jahaziel.storage.root:data}") String storageRoot) {
        this.storageRoot = SafetyPaths.root(storageRoot).toString();
    }

    /** Returns changes observed since an epoch-millisecond timestamp and advances the baseline. */
    public List<WatchChange> changesSince(WorkspaceId workspace, long sinceMillis) {
        synchronized (lockFor(workspace)) {
            Path home = SafetyPaths.home(storageRoot, workspace);
            Map<String, Observed> baseline = load(workspace);
            Map<String, Observed> current = observe(home, baseline);
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
                    changes.add(
                            new WatchChange(entry.getKey(), WatchChange.MODIFIED, now.modifiedAt));
                }
            }
            for (Map.Entry<String, Observed> entry : baseline.entrySet()) {
                if (!current.containsKey(entry.getKey())
                        && entry.getValue().modifiedAt > sinceMillis) {
                    changes.add(
                            new WatchChange(
                                    entry.getKey(),
                                    WatchChange.DELETED,
                                    entry.getValue().modifiedAt));
                }
            }
            save(workspace, current);
            return changes;
        }
    }

    private Object lockFor(WorkspaceId workspace) {
        Object lock = locks.get(workspace);
        if (lock == null) {
            Object created = new Object();
            Object existing = locks.putIfAbsent(workspace, created);
            lock = existing == null ? created : existing;
        }
        return lock;
    }

    private static Map<String, Observed> observe(Path root, Map<String, Observed> baseline) {
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
                            if (!directory.equals(root)
                                    && ignored(directory.getFileName().toString())) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attributes) {
                            if (!attributes.isRegularFile()) {
                                return FileVisitResult.CONTINUE;
                            }
                            String relative = root.relativize(file).toString().replace('\\', '/');
                            long stamp = attributes.lastModifiedTime().toMillis();
                            long modifiedAt = attributes.lastModifiedTime().toMillis();
                            long size = attributes.size();
                            Observed previous = baseline.get(relative);
                            String digest;
                            if (previous != null
                                    && previous.stamp == stamp
                                    && previous.size == size) {
                                digest = previous.hash;
                            } else {
                                try {
                                    digest = hash(file);
                                } catch (IOException failure) {
                                    throw new UncheckedIOException(failure);
                                }
                            }
                            result.put(relative, new Observed(modifiedAt, stamp, size, digest));
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
            long stamp = Long.MIN_VALUE;
            long size = -1L;
            String digest;
            if (parts.length >= 5) {
                stamp = Long.parseLong(parts[2]);
                size = Long.parseLong(parts[3]);
                digest = parts[4];
            } else if (parts.length == 4) {
                size = Long.parseLong(parts[2]);
                digest = parts[3];
            } else {
                digest = parts[2];
            }
            result.put(
                    SafetyPaths.decodeText(parts[0]),
                    new Observed(Long.parseLong(parts[1]), stamp, size, digest));
        }
        return result;
    }

    private void save(WorkspaceId workspace, Map<String, Observed> observed) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Observed> entry : observed.entrySet()) {
            Observed value = entry.getValue();
            text.append(SafetyPaths.encodeText(entry.getKey()))
                    .append(' ')
                    .append(value.modifiedAt)
                    .append(' ')
                    .append(value.stamp)
                    .append(' ')
                    .append(value.size)
                    .append(' ')
                    .append(value.hash)
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

    /** Hashes at most a bounded prefix; the digest is a change signal, not a whole-file proof. */
    private static String hash(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        byte[] buffer = new byte[8192];
        int remaining = MAX_HASH_BYTES;
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while (remaining > 0
                    && (read = input.read(buffer, 0, Math.min(buffer.length, remaining))) >= 0) {
                if (read == 0) {
                    continue;
                }
                digest.update(buffer, 0, read);
                remaining -= read;
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static final class Observed {
        private final long modifiedAt;
        private final long stamp;
        private final long size;
        private final String hash;

        private Observed(long modifiedAt, long stamp, long size, String hash) {
            this.modifiedAt = modifiedAt;
            this.stamp = stamp;
            this.size = size;
            this.hash = hash;
        }
    }
}
