/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

import java.nio.file.Path;

/** A snapshot presented as a restorable file version. */
public final class FileVersion {
    private final String id;
    private final String tag;
    private final String createdAt;
    private final Path snapshotPath;

    public FileVersion(String id, String tag, String createdAt, Path snapshotPath) {
        if (id == null || snapshotPath == null) {
            throw new IllegalArgumentException("id and snapshotPath are required");
        }
        this.id = id;
        this.tag = tag;
        this.createdAt = createdAt;
        this.snapshotPath = snapshotPath;
    }

    public String id() {
        return id;
    }

    public String tag() {
        return tag;
    }

    public String createdAt() {
        return createdAt;
    }

    public Path snapshotPath() {
        return snapshotPath;
    }
}
