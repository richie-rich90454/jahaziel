/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.domain;

/** How a snapshot was triggered. */
public final class SnapshotKind {
    /** Platform-managed daily snapshot. Does not count against quota. */
    public static final SnapshotKind AUTO = new SnapshotKind("AUTO");

    /** User-triggered snapshot. Counts against quota. */
    public static final SnapshotKind MANUAL = new SnapshotKind("MANUAL");

    /** Taken before administrative deletion. Kept 90 days. */
    public static final SnapshotKind PRE_DELETE = new SnapshotKind("PRE_DELETE");

    private final String name;

    private SnapshotKind(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
