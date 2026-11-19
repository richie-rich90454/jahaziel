/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

/** Counts of a completed bulk file operation. */
public final class BulkResult {
    private final String operation;
    private final int applied;
    private final int skipped;

    public BulkResult(String operation, int applied, int skipped) {
        this.operation = operation;
        this.applied = applied;
        this.skipped = skipped;
    }

    public String getOperation() {
        return operation;
    }

    public int getApplied() {
        return applied;
    }

    public int getSkipped() {
        return skipped;
    }
}
