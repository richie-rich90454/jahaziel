/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

/** One item in a workspace trash. */
public final class TrashEntry {
    private final String id;
    private final String path;
    private final long deletedAt;
    private final long size;
    private final String kind;

    public TrashEntry(String id, String path, long deletedAt, long size, String kind) {
        this.id = id;
        this.path = path;
        this.deletedAt = deletedAt;
        this.size = size;
        this.kind = kind;
    }

    public String getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public long getDeletedAt() {
        return deletedAt;
    }

    public long getSize() {
        return size;
    }

    public String getKind() {
        return kind;
    }
}
