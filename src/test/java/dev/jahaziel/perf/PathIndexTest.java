/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

/** Rebuild and incremental-update behavior of the file-list lookup index. */
class PathIndexTest {
    @Test
    void rebuildAndLookup() {
        PathIndex index = new PathIndex();
        PathIndex.FileEntry first = new PathIndex.FileEntry("src/a.ts", "a.ts", false, 10);
        PathIndex.FileEntry second = new PathIndex.FileEntry("src/lib", "lib", true, 0);
        PathIndex.FileEntry third = new PathIndex.FileEntry("README.md", "README.md", false, 42);
        List<PathIndex.FileEntry> entries = new ArrayList<PathIndex.FileEntry>();
        entries.add(first);
        entries.add(second);
        entries.add(third);
        index.rebuild(entries);
        assertEquals(3, index.size());
        assertSame(first, index.get("src/a.ts"));
        assertTrue(index.contains("src/lib"));
        assertFalse(index.contains("missing"));
        assertNull(index.get("missing"));
        assertEquals(10, first.size());
        assertTrue(second.directory());
        assertEquals("README.md", third.name());
    }

    @Test
    void incrementalUpsertAndRemove() {
        PathIndex index = new PathIndex();
        List<PathIndex.FileEntry> entries = new ArrayList<PathIndex.FileEntry>();
        entries.add(new PathIndex.FileEntry("a", "a", false, 1));
        index.rebuild(entries);
        PathIndex.FileEntry added = new PathIndex.FileEntry("b", "b", false, 2);
        index.upsert(added);
        assertEquals(2, index.size());
        assertSame(added, index.get("b"));
        PathIndex.FileEntry replacement = new PathIndex.FileEntry("a", "a", false, 9);
        index.upsert(replacement);
        assertSame(replacement, index.get("a"));
        assertTrue(index.remove("b"));
        assertFalse(index.remove("b"));
        assertNull(index.get("b"));
        assertEquals(1, index.size());
    }

    @Test
    void rebuildClearsStaleRows() {
        PathIndex index = new PathIndex();
        List<PathIndex.FileEntry> entries = new ArrayList<PathIndex.FileEntry>();
        entries.add(new PathIndex.FileEntry("old", "old", false, 1));
        index.rebuild(entries);
        List<PathIndex.FileEntry> fresh = new ArrayList<PathIndex.FileEntry>();
        fresh.add(new PathIndex.FileEntry("new", "new", false, 1));
        index.rebuild(fresh);
        assertEquals(1, index.size());
        assertFalse(index.contains("old"));
        assertTrue(index.contains("new"));
    }

    @Test
    void allReturnsSortedSnapshot() {
        PathIndex index = new PathIndex();
        List<PathIndex.FileEntry> entries = new ArrayList<PathIndex.FileEntry>();
        entries.add(new PathIndex.FileEntry("z", "z", false, 1));
        entries.add(new PathIndex.FileEntry("a", "a", false, 1));
        index.rebuild(entries);
        List<PathIndex.FileEntry> snapshot = index.all();
        assertEquals("a", snapshot.get(0).path());
        assertEquals("z", snapshot.get(1).path());
        snapshot.clear();
        assertEquals(2, index.size());
    }

    @Test
    void emptyPathThrows() {
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new PathIndex.FileEntry("", "empty", false, 0);
                    }
                });
    }
}
