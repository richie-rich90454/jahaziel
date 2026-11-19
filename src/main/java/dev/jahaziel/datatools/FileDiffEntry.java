/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

/** One changed file between two snapshot versions. */
public final class FileDiffEntry {
    public static final String ADDED = "ADDED";
    public static final String MODIFIED = "MODIFIED";
    public static final String REMOVED = "REMOVED";

    private final String path;
    private final String change;

    public FileDiffEntry(String path, String change) {
        if (path == null || change == null) {
            throw new IllegalArgumentException("path and change are required");
        }
        this.path = path;
        this.change = change;
    }

    public String path() {
        return path;
    }

    public String change() {
        return change;
    }
}
