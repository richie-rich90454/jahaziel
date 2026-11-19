/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.datatools;

/** A search match: the workspace-relative path and a bounded snippet. */
public final class IndexHit {
    private final String path;
    private final String snippet;

    public IndexHit(String path, String snippet) {
        if (path == null) {
            throw new IllegalArgumentException("path is required");
        }
        this.path = path;
        this.snippet = snippet == null ? "" : snippet;
    }

    public String path() {
        return path;
    }

    public String snippet() {
        return snippet;
    }
}
