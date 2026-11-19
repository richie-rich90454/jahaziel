/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves caller-supplied relative names to files physically inside a trusted root. */
public final class PathGuard {
    private PathGuard() {}

    /** Returns the canonical, existing form of a trusted root. */
    public static Path root(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root is required");
        }
        try {
            return root.toAbsolutePath().normalize().toRealPath();
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot resolve root: " + root, failure);
        }
    }

    /**
     * Resolves a relative path against a trusted root and verifies, after symlink expansion, that
     * the result still lives inside that root. Absolute paths, drive letters and parent traversal
     * are rejected before any filesystem access.
     */
    public static Path confine(Path realRoot, String relative) {
        if (relative == null || relative.trim().isEmpty()) {
            throw new IllegalArgumentException("relative path is required");
        }
        String cleaned = relative.replace('\\', '/');
        if (cleaned.indexOf(':') >= 0) {
            throw new IllegalArgumentException("drive-qualified paths are rejected: " + relative);
        }
        Path child;
        try {
            child = Paths.get(cleaned);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("invalid relative path: " + relative, failure);
        }
        if (child.isAbsolute()) {
            throw new IllegalArgumentException("absolute paths are rejected: " + relative);
        }
        for (Path part : child) {
            if ("..".equals(part.toString())) {
                throw new IllegalArgumentException("parent traversal is rejected: " + relative);
            }
        }
        Path candidate = realRoot.resolve(child).normalize();
        try {
            Path resolved;
            if (Files.exists(candidate)) {
                resolved = candidate.toRealPath();
            } else {
                Path parent = candidate.getParent();
                if (parent == null) {
                    throw new IllegalArgumentException("relative path is required: " + relative);
                }
                resolved = parent.toRealPath().resolve(candidate.getFileName());
            }
            if (!resolved.startsWith(realRoot)) {
                throw new IllegalArgumentException("path escapes its root: " + relative);
            }
            return resolved;
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot resolve path: " + relative, failure);
        }
    }
}
