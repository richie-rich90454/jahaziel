/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import dev.jahaziel.security.CanonicalPathGuard;
import dev.jahaziel.workspace.domain.WorkspaceId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Confined paths for safety state. Every file this package owns lives below the jar data root; no
 * state is written to the operating system temp directory or the user home.
 */
public final class SafetyPaths {
    private SafetyPaths() {}

    /** Returns the normalized data root. */
    public static Path root(String storageRoot) {
        if (storageRoot == null || storageRoot.isBlank()) {
            throw new IllegalArgumentException("storageRoot must not be blank");
        }
        return Path.of(storageRoot).toAbsolutePath().normalize();
    }

    /** Returns the workspace home folder. */
    public static Path home(String storageRoot, WorkspaceId workspace) {
        return root(storageRoot)
                .resolve("workspaces")
                .resolve(workspace.value().toString())
                .resolve("home");
    }

    /** Returns the operation-history folder of one workspace. */
    public static Path history(String storageRoot, WorkspaceId workspace) {
        return root(storageRoot).resolve("history").resolve(workspace.value().toString());
    }

    /** Returns the trash folder of one workspace. */
    public static Path trash(String storageRoot, WorkspaceId workspace) {
        return root(storageRoot).resolve("trash").resolve(workspace.value().toString());
    }

    /** Returns the watch baseline folder of one workspace. */
    public static Path watch(String storageRoot, WorkspaceId workspace) {
        return root(storageRoot).resolve("watch").resolve(workspace.value().toString());
    }

    /** Returns the shared flags folder. */
    public static Path flags(String storageRoot) {
        return root(storageRoot).resolve("flags");
    }

    /** Returns the template library folder. */
    public static Path templates(String storageRoot) {
        return root(storageRoot).resolve("templates");
    }

    /**
     * Resolves a caller-supplied relative path inside a root, rejecting traversal, links that
     * escape the root, and Windows device names. The root is created when absent.
     *
     * @param root the containment root
     * @param relative the workspace-relative path
     * @return the confined absolute path
     */
    public static Path resolveInside(Path root, String relative) {
        if (root == null || relative == null || relative.isEmpty() || relative.isBlank()) {
            throw new IllegalArgumentException("a relative path is required");
        }
        if (relative.indexOf('\0') >= 0 || relative.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("the relative path is invalid");
        }
        try {
            Files.createDirectories(root);
            Path candidate = Path.of(relative);
            if (candidate.isAbsolute()) {
                throw new IllegalArgumentException("absolute paths are not allowed");
            }
            for (int index = 0; index < candidate.getNameCount(); index++) {
                if ("..".equals(candidate.getName(index).toString())) {
                    throw new IllegalArgumentException("parent traversal is not allowed");
                }
            }
            if (!CanonicalPathGuard.check(root, candidate)) {
                throw new IllegalArgumentException("the path escapes its workspace root");
            }
            Path realRoot = root.toRealPath();
            Path resolved = realRoot.resolve(candidate).normalize();
            if (!resolved.startsWith(realRoot)) {
                throw new IllegalArgumentException("the path escapes its workspace root");
            }
            return resolved;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Returns whether a path is part of the repository metadata we never rewrite. */
    public static boolean isGitPath(String relative) {
        if (relative == null) {
            return false;
        }
        String normalized = relative.replace('\\', '/');
        return normalized.equals(".git") || normalized.startsWith(".git/");
    }

    /** Encodes bytes for the line-oriented journal format. A null value becomes a dash. */
    public static String encode(byte[] value) {
        if (value == null) {
            return "-";
        }
        return Base64.getEncoder().encodeToString(value);
    }

    /** Decodes a journal field. A dash means null. */
    public static byte[] decode(String value) {
        if (value == null || value.equals("-")) {
            return null;
        }
        return Base64.getDecoder().decode(value);
    }

    /** Encodes a string field. */
    public static String encodeText(String value) {
        if (value == null) {
            return "-";
        }
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes a string field. */
    public static String decodeText(String value) {
        byte[] bytes = decode(value);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /** Reads a UTF-8 text file, returning an empty string when it does not exist. */
    public static String readText(Path file) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read " + file, failure);
        }
    }

    /** Writes a UTF-8 text file atomically enough for a single-writer journal. */
    public static void writeText(Path file, String text) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = Files.createTempFile(parent, ".journal-", ".tmp");
            try {
                Files.write(temporary, text.getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(
                            temporary,
                            file,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot write " + file, failure);
        }
    }
}
