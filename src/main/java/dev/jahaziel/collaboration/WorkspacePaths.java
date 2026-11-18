/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.collaboration;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Validates that a file path stays inside a workspace root. */
public final class WorkspacePaths {
    private WorkspacePaths() {}

    /** Returns a normalized relative path, rejecting traversal and absolute paths. */
    public static String validate(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            throw new IllegalArgumentException("path is required");
        }
        String cleaned = raw.trim().replace('\\', '/');
        if (cleaned.indexOf('\0') >= 0
                || cleaned.startsWith("/")
                || cleaned.startsWith("../")
                || cleaned.equals("..")) {
            throw new IllegalArgumentException("path must stay inside the workspace");
        }
        Path normalized = Paths.get(cleaned).normalize();
        String result = normalized.toString().replace('\\', '/');
        if (normalized.isAbsolute()
                || result.equals("..")
                || result.startsWith("../")
                || result.equals(".")) {
            throw new IllegalArgumentException("path must stay inside the workspace");
        }
        return result;
    }

    /** True when the candidate path resolves within the given workspace-relative root. */
    public static boolean contained(String candidate, String root) {
        String path = validate(candidate);
        String base = root == null || root.trim().length() == 0 ? "" : validate(root);
        if (base.length() == 0) {
            return true;
        }
        return path.equals(base) || path.startsWith(base + "/");
    }
}
