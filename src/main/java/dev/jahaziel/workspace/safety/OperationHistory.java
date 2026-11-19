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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only journal of reversible file operations with undo and redo pointers. State is kept
 * under the jar data directory and never touches git metadata.
 */
@Component
public final class OperationHistory {
    private final String storageRoot;
    private final Map<String, State> states = new HashMap<String, State>();

    public OperationHistory(@Value("${jahaziel.storage.root:data}") String storageRoot) {
        this.storageRoot = SafetyPaths.root(storageRoot).toString();
    }

    /** Returns the operations that can currently be undone, oldest first. */
    public synchronized List<OperationRecord> list(WorkspaceId workspace) {
        State state = load(workspace);
        List<OperationRecord> result = new ArrayList<OperationRecord>();
        for (int index = 0; index < state.applied; index++) {
            result.add(state.records.get(index));
        }
        return result;
    }

    /** Records a text or binary overwrite. */
    public synchronized OperationRecord recordWrite(
            WorkspaceId workspace, String path, byte[] oldBytes, byte[] newBytes, String actor) {
        requireNotGit(path);
        return append(workspace, OperationRecord.WRITE, path, null, oldBytes, newBytes, actor);
    }

    /** Records a newly created file. */
    public synchronized OperationRecord recordCreate(
            WorkspaceId workspace, String path, byte[] newBytes, String actor) {
        requireNotGit(path);
        return append(workspace, OperationRecord.CREATE, path, null, null, newBytes, actor);
    }

    /** Records a deletion, keeping the removed bytes for restore. */
    public synchronized OperationRecord recordDelete(
            WorkspaceId workspace, String path, byte[] oldBytes, String actor) {
        requireNotGit(path);
        return append(workspace, OperationRecord.DELETE, path, null, oldBytes, null, actor);
    }

    /** Records a rename or move. */
    public synchronized OperationRecord recordRename(
            WorkspaceId workspace, String oldPath, String newPath, String actor) {
        requireNotGit(oldPath);
        requireNotGit(newPath);
        return append(workspace, OperationRecord.RENAME, newPath, oldPath, null, null, actor);
    }

    /** Records a copy. */
    public synchronized OperationRecord recordCopy(
            WorkspaceId workspace, String source, String destination, String actor) {
        requireNotGit(source);
        requireNotGit(destination);
        return append(workspace, OperationRecord.COPY, destination, source, null, null, actor);
    }

    /** Records a directory creation. */
    public synchronized OperationRecord recordMkdir(
            WorkspaceId workspace, String path, String actor) {
        requireNotGit(path);
        return append(workspace, OperationRecord.MKDIR, path, null, null, null, actor);
    }

    /** Reverses one applied operation. Returns an empty list when nothing can be undone. */
    public synchronized List<OperationRecord> undo(WorkspaceId workspace) {
        State state = load(workspace);
        List<OperationRecord> result = new ArrayList<OperationRecord>();
        if (state.applied <= 0) {
            return result;
        }
        OperationRecord record = state.records.get(state.applied - 1);
        apply(workspace, record, false);
        state.applied--;
        save(workspace, state);
        result.add(record);
        return result;
    }

    /** Re-applies one undone operation. Returns an empty list when nothing can be redone. */
    public synchronized List<OperationRecord> redo(WorkspaceId workspace) {
        State state = load(workspace);
        List<OperationRecord> result = new ArrayList<OperationRecord>();
        if (state.applied >= state.records.size()) {
            return result;
        }
        OperationRecord record = state.records.get(state.applied);
        apply(workspace, record, true);
        state.applied++;
        save(workspace, state);
        result.add(record);
        return result;
    }

    private OperationRecord append(
            WorkspaceId workspace,
            String kind,
            String path,
            String oldPath,
            byte[] oldBytes,
            byte[] newBytes,
            String actor) {
        State state = load(workspace);
        while (state.records.size() > state.applied) {
            state.records.remove(state.records.size() - 1);
        }
        state.sequence++;
        OperationRecord record =
                new OperationRecord(
                        state.sequence,
                        UUID.randomUUID().toString(),
                        kind,
                        path,
                        oldPath,
                        oldBytes,
                        newBytes,
                        System.currentTimeMillis(),
                        actor);
        state.records.add(record);
        state.applied++;
        save(workspace, state);
        return record;
    }

    private void apply(WorkspaceId workspace, OperationRecord record, boolean forward) {
        Path home = SafetyPaths.home(storageRoot, workspace);
        if (OperationRecord.RENAME.equals(record.kind())) {
            String from = forward ? record.oldPath() : record.getPath();
            String to = forward ? record.getPath() : record.oldPath();
            move(home, from, to);
            return;
        }
        if (OperationRecord.COPY.equals(record.kind())) {
            if (forward) {
                copy(home, record.oldPath(), record.getPath());
            } else {
                delete(home, record.getPath());
            }
            return;
        }
        if (OperationRecord.MKDIR.equals(record.kind())) {
            if (forward) {
                mkdir(home, record.getPath());
            } else {
                delete(home, record.getPath());
            }
            return;
        }
        byte[] content = forward ? record.newBytes() : record.oldBytes();
        if (content == null) {
            delete(home, record.getPath());
        } else {
            write(home, record.getPath(), content);
        }
    }

    private static void write(Path home, String relative, byte[] content) {
        Path target = SafetyPaths.resolveInside(home, relative);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, content);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot replay file operation", failure);
        }
    }

    private static void delete(Path home, String relative) {
        Path target = SafetyPaths.resolveInside(home, relative);
        try {
            Files.deleteIfExists(target);
        } catch (java.nio.file.DirectoryNotEmptyException ignored) {
            // Replaying a directory creation keeps its contents; only an empty directory is
            // removed.
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot replay delete", failure);
        }
    }

    private static void move(Path home, String from, String to) {
        Path source = SafetyPaths.resolveInside(home, from);
        Path target = SafetyPaths.resolveInside(home, to);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot replay move", failure);
        }
    }

    private static void copy(Path home, String from, String to) {
        Path source = SafetyPaths.resolveInside(home, from);
        Path target = SafetyPaths.resolveInside(home, to);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot replay copy", failure);
        }
    }

    private static void mkdir(Path home, String relative) {
        Path target = SafetyPaths.resolveInside(home, relative);
        try {
            Files.createDirectories(target);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot replay directory creation", failure);
        }
    }

    private static void requireNotGit(String path) {
        if (SafetyPaths.isGitPath(path)) {
            throw new IllegalArgumentException("git metadata cannot be changed through history");
        }
    }

    private State load(WorkspaceId workspace) {
        State cached = states.get(workspace.value().toString());
        if (cached != null) {
            return cached;
        }
        State state = new State();
        Path file = SafetyPaths.history(storageRoot, workspace).resolve("journal.dat");
        String text = SafetyPaths.readText(file);
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(" ", -1);
            if (parts[0].equals("SEQ") && parts.length >= 2) {
                state.sequence = Long.parseLong(parts[1]);
            } else if (parts[0].equals("APPLIED") && parts.length >= 2) {
                state.applied = Integer.parseInt(parts[1]);
            } else if (parts[0].equals("OP") && parts.length >= 9) {
                state.records.add(
                        new OperationRecord(
                                Long.parseLong(parts[1]),
                                SafetyPaths.decodeText(parts[2]),
                                SafetyPaths.decodeText(parts[3]),
                                SafetyPaths.decodeText(parts[4]),
                                SafetyPaths.decodeText(parts[5]),
                                SafetyPaths.decode(parts[6]),
                                SafetyPaths.decode(parts[7]),
                                Long.parseLong(parts[8]),
                                parts.length >= 10 ? SafetyPaths.decodeText(parts[9]) : "system"));
            }
        }
        states.put(workspace.value().toString(), state);
        return state;
    }

    private void save(WorkspaceId workspace, State state) {
        StringBuilder text = new StringBuilder();
        text.append("SEQ ").append(state.sequence).append('\n');
        text.append("APPLIED ").append(state.applied).append('\n');
        for (int index = 0; index < state.records.size(); index++) {
            OperationRecord record = state.records.get(index);
            text.append("OP ")
                    .append(record.getSequence())
                    .append(' ')
                    .append(SafetyPaths.encodeText(record.getId()))
                    .append(' ')
                    .append(SafetyPaths.encodeText(record.getKind()))
                    .append(' ')
                    .append(SafetyPaths.encodeText(record.getPath()))
                    .append(' ')
                    .append(SafetyPaths.encodeText(record.getOldPath()))
                    .append(' ')
                    .append(SafetyPaths.encode(record.oldBytes()))
                    .append(' ')
                    .append(SafetyPaths.encode(record.newBytes()))
                    .append(' ')
                    .append(record.getTimestamp())
                    .append(' ')
                    .append(SafetyPaths.encodeText(record.getActor()))
                    .append('\n');
        }
        Path file = SafetyPaths.history(storageRoot, workspace).resolve("journal.dat");
        SafetyPaths.writeText(file, text.toString());
    }

    private static final class State {
        private long sequence;
        private int applied;
        private final List<OperationRecord> records = new ArrayList<OperationRecord>();
    }
}
