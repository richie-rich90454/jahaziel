/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared file storage for every agent feature. All paths resolve under one data root, symbolic
 * links are rejected, and writes go through a temporary file so readers never see a partial file.
 */
public final class AgentStore {
    private static final int MAX_SEGMENT_LENGTH = 96;
    private static final int MAX_RELATIVE_LENGTH = 1024;

    private final Path dataRoot;
    private final ObjectMapper json;

    public AgentStore(Path dataRoot, ObjectMapper json) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot").toAbsolutePath().normalize();
        this.json = Objects.requireNonNull(json, "json");
    }

    /** Returns the jar-adjacent data root. */
    public Path dataRoot() {
        return dataRoot;
    }

    /** Returns {@code data/agent}. */
    public Path agentRoot() {
        return containedChild(dataRoot, "agent");
    }

    /** Returns the PTY session metadata and transcript root. */
    public Path sessionRoot() {
        return containedChild(agentRoot(), "sessions");
    }

    /** Returns the prompt template root. */
    public Path promptRoot() {
        return containedChild(agentRoot(), "prompts");
    }

    /** Returns the staged patch root. */
    public Path patchRoot() {
        return containedChild(agentRoot(), "patches");
    }

    /** Returns the chat history root. */
    public Path chatRoot() {
        return containedChild(agentRoot(), "chat");
    }

    /** Returns the usage meter root. */
    public Path usageRoot() {
        return containedChild(agentRoot(), "usage");
    }

    /** Returns the workspace home used for patch path containment. */
    public Path workspaceHome(UUID workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        return containedChild(dataRoot, "workspaces", workspaceId.toString(), "home");
    }

    /**
     * Resolves a workspace-relative file path and proves it stays inside that workspace home.
     *
     * @param workspaceId the owning workspace
     * @param relative the browser-supplied relative path
     * @return the resolved path
     */
    public Path resolveWorkspacePath(UUID workspaceId, String relative) {
        String clean = requireRelativePath(relative);
        Path home = workspaceHome(workspaceId);
        Path resolved = home.resolve(clean).normalize();
        if (!resolved.startsWith(home)) {
            throw new AgentException("path escapes the workspace");
        }
        rejectSymlinks(resolved);
        return resolved;
    }

    /**
     * Resolves a chain of safe name segments under a base directory.
     *
     * @param base the base directory
     * @param segments the path segments
     * @return the resolved path, guaranteed to stay under the base
     */
    public Path containedChild(Path base, String... segments) {
        Path root = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        if (!root.startsWith(dataRoot) && !root.equals(dataRoot)) {
            throw new AgentException("base path is outside the data directory");
        }
        Path result = root;
        for (String segment : segments) {
            if (!isSafeSegment(segment)) {
                throw new AgentException("unsafe path segment");
            }
            result = result.resolve(segment).normalize();
        }
        if (!result.startsWith(root) && !result.equals(root)) {
            throw new AgentException("path escapes its base directory");
        }
        rejectSymlinks(result);
        return result;
    }

    /** Writes a JSON value atomically. The target must stay under the data root. */
    public void write(Path target, Object value) {
        Path file = requireInsideData(target);
        ensureParent(file);
        Path temporary = file.resolveSibling(file.getFileName().toString() + ".tmp");
        rejectSymlinks(temporary);
        try {
            Files.writeString(temporary, json.writeValueAsString(value), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "cannot write agent state under the data directory", failure);
        }
    }

    /** Reads a JSON value, returning null when the file does not exist. */
    public <T> T read(Path target, Class<T> type) {
        Path file = requireInsideData(target);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        rejectSymlinks(file);
        try {
            return json.readValue(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "cannot read agent state under the data directory", failure);
        }
    }

    /** True when the target exists as a regular file inside the data root. */
    public boolean exists(Path target) {
        Path file = requireInsideData(target);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
    }

    /** Appends one line to an audit-style transcript inside the data root. */
    public void appendLine(Path target, String line) {
        Path file = requireInsideData(target);
        ensureParent(file);
        rejectSymlinks(file);
        try {
            Files.writeString(
                    file,
                    line + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "cannot append agent state under the data directory", failure);
        }
    }

    /** Reads all lines of a transcript inside the data root. */
    public List<String> readLines(Path target) {
        Path file = requireInsideData(target);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return new ArrayList<String>();
        }
        rejectSymlinks(file);
        try {
            return new ArrayList<String>(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read agent transcript", failure);
        }
    }

    /** Returns the regular files directly inside a directory, never following symlinks. */
    public List<Path> listFiles(Path directory) {
        Path root = requireInsideData(directory);
        List<Path> result = new ArrayList<Path>();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return result;
        }
        rejectSymlinks(root);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(child)) {
                    result.add(child);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "cannot list agent state under the data directory", failure);
        }
        return result;
    }

    /** Deletes one regular file inside the data root. */
    public void delete(Path target) {
        Path file = requireInsideData(target);
        rejectSymlinks(file);
        try {
            Files.deleteIfExists(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "cannot delete agent state under the data directory", failure);
        }
    }

    /** Validates one path segment: no separators, no traversal, no control characters. */
    public static boolean isSafeSegment(String segment) {
        if (segment == null || segment.isEmpty() || segment.length() > MAX_SEGMENT_LENGTH) {
            return false;
        }
        if (".".equals(segment) || "..".equals(segment)) {
            return false;
        }
        for (int index = 0; index < segment.length(); index++) {
            char character = segment.charAt(index);
            if (character < 0x20
                    || character == '/'
                    || character == '\\'
                    || character == ':'
                    || character == 0x7f) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates a workspace-relative path. Absolute paths, drive letters, backslashes and traversal
     * segments are rejected before any file is touched.
     */
    public static String requireRelativePath(String relative) {
        if (relative == null || relative.isEmpty() || relative.length() > MAX_RELATIVE_LENGTH) {
            throw new AgentException("file path must not be blank or oversized");
        }
        if (relative.charAt(0) == '/' || relative.charAt(0) == '\\') {
            throw new AgentException("absolute file paths are not allowed");
        }
        if (relative.length() >= 2
                && Character.isLetter(relative.charAt(0))
                && relative.charAt(1) == ':') {
            throw new AgentException("drive-qualified file paths are not allowed");
        }
        String[] parts = relative.split("/", -1);
        StringBuilder clean = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new AgentException("unsafe file path segment");
            }
            if (part.indexOf('\\') >= 0 || part.indexOf(0) >= 0) {
                throw new AgentException("unsafe file path segment");
            }
            for (int offset = 0; offset < part.length(); offset++) {
                char character = part.charAt(offset);
                if (character < 0x20) {
                    throw new AgentException("unsafe file path segment");
                }
            }
            if (index > 0) {
                clean.append('/');
            }
            clean.append(part);
        }
        return clean.toString();
    }

    private Path requireInsideData(Path target) {
        Path file = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        if (!file.startsWith(dataRoot)) {
            throw new AgentException("path is outside the data directory");
        }
        rejectSymlinks(file);
        return file;
    }

    private void ensureParent(Path target) {
        Path parent = target.getParent();
        if (parent == null || !parent.startsWith(dataRoot)) {
            throw new AgentException("path is outside the data directory");
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot create agent state directory", failure);
        }
        rejectSymlinks(parent);
    }

    private static void rejectSymlinks(Path target) {
        Path cursor = target;
        while (cursor != null) {
            if (Files.isSymbolicLink(cursor)) {
                throw new AgentException("symbolic links are not allowed in agent storage");
            }
            cursor = cursor.getParent();
        }
    }
}
