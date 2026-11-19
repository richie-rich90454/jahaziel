/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.diff;

import java.util.ArrayList;
import java.util.List;

/** Added, removed, and modified paths comparing a snapshot with a workspace tree. */
public final class WorkspaceDiff {
    private final List<String> added = new ArrayList<String>();
    private final List<String> removed = new ArrayList<String>();
    private final List<String> modified = new ArrayList<String>();

    public List<String> getAdded() {
        return added;
    }

    public List<String> getRemoved() {
        return removed;
    }

    public List<String> getModified() {
        return modified;
    }
}
