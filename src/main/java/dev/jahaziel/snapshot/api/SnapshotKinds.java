/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.api;

import dev.jahaziel.snapshot.domain.SnapshotKind;

/** Stored snapshot kind forms. The database uses dashes where the kind name uses underscores. */
public final class SnapshotKinds {
    private SnapshotKinds() {}

    /**
     * Maps a kind to its stored form.
     *
     * @param kind the kind
     * @return the lowercase stored form
     */
    public static String toString(SnapshotKind kind) {
        if (kind == SnapshotKind.AUTO) {
            return "auto";
        }
        if (kind == SnapshotKind.MANUAL) {
            return "manual";
        }
        if (kind == SnapshotKind.PRE_DELETE) {
            return "pre-delete";
        }
        throw new IllegalStateException("unknown snapshot kind " + kind);
    }

    /**
     * Parses a stored kind.
     *
     * @param kind the stored form
     * @return the kind
     */
    public static SnapshotKind fromString(String kind) {
        if ("auto".equals(kind)) {
            return SnapshotKind.AUTO;
        }
        if ("manual".equals(kind)) {
            return SnapshotKind.MANUAL;
        }
        if ("pre-delete".equals(kind)) {
            return SnapshotKind.PRE_DELETE;
        }
        throw new IllegalStateException("unknown snapshot kind " + kind);
    }
}
