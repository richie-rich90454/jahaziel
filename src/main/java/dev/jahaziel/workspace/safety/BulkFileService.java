/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import dev.jahaziel.workspace.api.WorkspaceFiles;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Bounded multi-path copy, move, and delete over the existing workspace file API. */
@Component
public final class BulkFileService {
    public static final String COPY = "copy";
    public static final String MOVE = "move";
    public static final String DELETE = "delete";

    private static final int MAX_PATHS = 100;
    private static final long MAX_TOTAL_BYTES = 20L * 1024L * 1024L;

    private final WorkspaceFiles files;
    private final OperationHistory history;
    private final String storageRoot;

    public BulkFileService(
            WorkspaceFiles files,
            OperationHistory history,
            @Value("${jahaziel.storage.root:data}") String storageRoot) {
        if (files == null) {
            throw new IllegalArgumentException("files is required");
        }
        if (history == null) {
            throw new IllegalArgumentException("history is required");
        }
        this.files = files;
        this.history = history;
        this.storageRoot = SafetyPaths.root(storageRoot).toString();
    }

    /** Applies one bulk operation. All paths are validated before any file is touched. */
    public synchronized BulkResult bulk(
            WorkspaceId workspace,
            String operation,
            List<String> paths,
            String destination,
            String actor) {
        if (operation == null
                || (!COPY.equals(operation)
                        && !MOVE.equals(operation)
                        && !DELETE.equals(operation))) {
            throw new IllegalArgumentException("operation must be copy, move, or delete");
        }
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("at least one path is required");
        }
        if (paths.size() > MAX_PATHS) {
            throw new IllegalArgumentException("too many paths in one bulk operation");
        }
        Path home = SafetyPaths.home(storageRoot, workspace);
        List<String> wanted = new ArrayList<String>();
        long total = 0;
        for (int index = 0; index < paths.size(); index++) {
            String path = paths.get(index);
            if (path == null || SafetyPaths.isGitPath(path)) {
                throw new IllegalArgumentException("path is invalid");
            }
            Path resolved = SafetyPaths.resolveInside(home, path);
            if (!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("path does not exist");
            }
            wanted.add(path);
            try {
                total += Files.size(resolved);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            if (total > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("bulk operation exceeds the byte limit");
            }
        }
        if (!DELETE.equals(operation)) {
            if (destination == null || destination.isBlank()) {
                throw new IllegalArgumentException("destination is required");
            }
            SafetyPaths.resolveInside(home, destination);
        }
        WorkspaceFiles.Operations operations = files.forWorkspace(workspace);
        int applied = 0;
        int skipped = 0;
        for (int index = 0; index < wanted.size(); index++) {
            String path = wanted.get(index);
            try {
                if (DELETE.equals(operation)) {
                    byte[] old = Files.readAllBytes(SafetyPaths.resolveInside(home, path));
                    operations.delete(path);
                    history.recordDelete(workspace, path, old, actor);
                } else if (MOVE.equals(operation)) {
                    String target = destinationFor(path, destination, wanted.size());
                    ensureParent(home, target);
                    operations.rename(path, target);
                    history.recordRename(workspace, path, target, actor);
                } else {
                    String target = destinationFor(path, destination, wanted.size());
                    ensureParent(home, target);
                    WorkspaceFiles.Document source = operations.read(path);
                    operations.save(target, source.content(), null, true);
                    history.recordCopy(workspace, path, target, actor);
                }
                applied++;
            } catch (IllegalArgumentException conflict) {
                skipped++;
            } catch (UncheckedIOException failure) {
                skipped++;
            } catch (IOException failure) {
                skipped++;
            }
        }
        return new BulkResult(operation, applied, skipped);
    }

    private static String destinationFor(String path, String destination, int count) {
        if (count == 1) {
            return destination;
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash < 0 ? path : path.substring(slash + 1);
        return destination.endsWith("/") ? destination + name : destination + "/" + name;
    }

    private static void ensureParent(Path home, String relative) {
        Path resolved = SafetyPaths.resolveInside(home, relative);
        Path parent = resolved.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
