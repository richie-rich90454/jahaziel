/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

/** One external change observed by polling file timestamps and hashes. */
public final class WatchChange {
    public static final String ADDED = "ADDED";
    public static final String MODIFIED = "MODIFIED";
    public static final String DELETED = "DELETED";

    private final String path;
    private final String kind;
    private final long modifiedAt;

    public WatchChange(String path, String kind, long modifiedAt) {
        this.path = path;
        this.kind = kind;
        this.modifiedAt = modifiedAt;
    }

    public String getPath() {
        return path;
    }

    public String getKind() {
        return kind;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }
}
