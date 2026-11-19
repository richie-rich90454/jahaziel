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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Workspace-scoped trash with restore, permanent delete, time-to-live, and byte accounting. Every
 * trashed item is stored below the jar data directory.
 */
@Component
public final class TrashService {
    private static final String FILE = "FILE";
    private static final String DIRECTORY = "DIRECTORY";

    private final String storageRoot;
    private final long ttlMillis;
    private final long quotaBytes;

    public TrashService(
            @Value("${jahaziel.storage.root:data}") String storageRoot,
            @Value("${jahaziel.trash.ttl-ms:604800000}") long ttlMillis,
            @Value("${jahaziel.trash.quota-bytes:104857600}") long quotaBytes) {
        this.storageRoot = SafetyPaths.root(storageRoot).toString();
        this.ttlMillis = ttlMillis;
        this.quotaBytes = quotaBytes;
    }

    /** Moves a file or directory into the trash. */
    public synchronized TrashEntry trash(
            WorkspaceId workspace, String relativePath, String actor) {
        Path home = SafetyPaths.home(storageRoot, workspace);
        Path source = SafetyPaths.resolveInside(home, relativePath);
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("path does not exist");
        }
        if (source.equals(home.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("workspace root cannot be trashed");
        }
        List<TrashEntry> entries = load(workspace);
        purgeExpired(workspace, entries);
        long size;
        try {
            size = measure(source);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot measure trashed item", failure);
        }
        long used = used(entries);
        if (used + size > quotaBytes) {
            save(workspace, entries);
            throw new IllegalStateException("trash quota exceeded");
        }
        String id = UUID.randomUUID().toString();
        String kind = Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) ? DIRECTORY : FILE;
        Path payload = payloadRoot(workspace, id).resolve(relativePath.replace('/', java.io.File.separatorChar));
        try {
            copyTree(source, payload);
            deleteTree(source);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot move item to trash", failure);
        }
        TrashEntry entry = new TrashEntry(id, relativePath, System.currentTimeMillis(), size, kind);
        entries.add(entry);
        save(workspace, entries);
        return entry;
    }

    /** Returns the live entries of a workspace, purging items past their time-to-live. */
    public synchronized List<TrashEntry> list(WorkspaceId workspace) {
        List<TrashEntry> entries = load(workspace);
        if (purgeExpired(workspace, entries)) {
            save(workspace, entries);
        }
        List<TrashEntry> result = new ArrayList<TrashEntry>();
        result.addAll(entries);
        return result;
    }

    /** Restores one trashed item to its original path. */
    public synchronized TrashEntry restore(WorkspaceId workspace, String itemId) {
        List<TrashEntry> entries = load(workspace);
        TrashEntry found = find(entries, itemId);
        Path stored = payloadRoot(workspace, itemId)
                .resolve(found.getPath().replace('/', java.io.File.separatorChar));
        Path home = SafetyPaths.home(storageRoot, workspace);
        Path target = SafetyPaths.resolveInside(home, found.getPath());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("restore target already exists");
        }
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(stored, target, StandardCopyOption.REPLACE_EXISTING);
            deleteTree(payloadRoot(workspace, itemId));
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot restore trashed item", failure);
        }
        entries.remove(found);
        save(workspace, entries);
        return found;
    }

    /** Permanently deletes one trashed item. */
    public synchronized void purge(WorkspaceId workspace, String itemId) {
        List<TrashEntry> entries = load(workspace);
        TrashEntry found = find(entries, itemId);
        try {
            deleteTree(payloadRoot(workspace, itemId));
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot purge trashed item", failure);
        }
        entries.remove(found);
        save(workspace, entries);
    }

    /** Permanently deletes every trashed item of a workspace. */
    public synchronized void empty(WorkspaceId workspace) {
        List<TrashEntry> entries = load(workspace);
        for (int index = 0; index < entries.size(); index++) {
            try {
                deleteTree(payloadRoot(workspace, entries.get(index).getId()));
            } catch (IOException failure) {
                throw new UncheckedIOException("cannot empty trash", failure);
            }
        }
        entries.clear();
        save(workspace, entries);
    }

    /** Returns the bytes currently held by the trash of a workspace. */
    public synchronized long usedBytes(WorkspaceId workspace) {
        return used(load(workspace));
    }

    private static TrashEntry find(List<TrashEntry> entries, String itemId) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).getId().equals(itemId)) {
                return entries.get(index);
            }
        }
        throw new IllegalArgumentException("trash item not found");
    }

    private Path payloadRoot(WorkspaceId workspace, String itemId) {
        return SafetyPaths.trash(storageRoot, workspace).resolve("items").resolve(itemId).resolve("payload");
    }

    private long used(List<TrashEntry> entries) {
        long total = 0;
        for (int index = 0; index < entries.size(); index++) {
            total += entries.get(index).getSize();
        }
        return total;
    }

    private boolean purgeExpired(WorkspaceId workspace, List<TrashEntry> entries) {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int index = entries.size() - 1; index >= 0; index--) {
            TrashEntry entry = entries.get(index);
            if (now - entry.getDeletedAt() >= ttlMillis) {
                try {
                    deleteTree(payloadRoot(workspace, entry.getId()));
                } catch (IOException failure) {
                    throw new UncheckedIOException("cannot expire trashed item", failure);
                }
                entries.remove(index);
                changed = true;
            }
        }
        return changed;
    }

    private List<TrashEntry> load(WorkspaceId workspace) {
        List<TrashEntry> entries = new ArrayList<TrashEntry>();
        Path file = SafetyPaths.trash(storageRoot, workspace).resolve("index.dat");
        String[] lines = SafetyPaths.readText(file).split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(" ", -1);
            if (parts.length < 5) {
                continue;
            }
            entries.add(
                    new TrashEntry(
                            parts[0],
                            SafetyPaths.decodeText(parts[1]),
                            Long.parseLong(parts[2]),
                            Long.parseLong(parts[3]),
                            parts[4]));
        }
        return entries;
    }

    private void save(WorkspaceId workspace, List<TrashEntry> entries) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < entries.size(); index++) {
            TrashEntry entry = entries.get(index);
            text.append(entry.getId())
                    .append(' ')
                    .append(SafetyPaths.encodeText(entry.getPath()))
                    .append(' ')
                    .append(entry.getDeletedAt())
                    .append(' ')
                    .append(entry.getSize())
                    .append(' ')
                    .append(entry.getKind())
                    .append('\n');
        }
        SafetyPaths.writeText(SafetyPaths.trash(storageRoot, workspace).resolve("index.dat"), text.toString());
    }

    private static long measure(Path root) throws IOException {
        final long[] total = new long[1];
        Files.walkFileTree(
                root,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        total[0] += attributes.size();
                        return FileVisitResult.CONTINUE;
                    }
                });
        return total[0];
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        Files.walkFileTree(
                source,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory, BasicFileAttributes attributes) throws IOException {
                        Files.createDirectories(destination.resolve(source.relativize(directory).toString()));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                            throws IOException {
                        Path target = destination.resolve(source.relativize(file).toString());
                        Path parent = target.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(
                root,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                            throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                            throws IOException {
                        Files.deleteIfExists(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
