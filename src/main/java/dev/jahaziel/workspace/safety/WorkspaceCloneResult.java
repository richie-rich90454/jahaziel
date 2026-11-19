/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

/** Outcome of a clone: a fresh workspace id with its own process state. */
public final class WorkspaceCloneResult {
    private final String id;
    private final String name;
    private final int copiedFiles;

    public WorkspaceCloneResult(String id, String name, int copiedFiles) {
        this.id = id;
        this.name = name;
        this.copiedFiles = copiedFiles;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCopiedFiles() {
        return copiedFiles;
    }
}
