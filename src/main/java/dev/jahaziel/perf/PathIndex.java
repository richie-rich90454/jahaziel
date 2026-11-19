/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.perf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O(1) lookup index for frontend-style file listings. Callers rebuild it once per listing and then
 * apply single-file inserts, updates and removals incrementally.
 */
public final class PathIndex {
    /**
     * A file-list row. Paths are normalized by the caller; this index only stores and matches them.
     */
    public static final class FileEntry {
        private final String path;
        private final String name;
        private final boolean directory;
        private final long size;

        public FileEntry(String path, String name, boolean directory, long size) {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("path must not be empty");
            }
            this.path = path;
            this.name = name == null ? "" : name;
            this.directory = directory;
            this.size = size;
        }

        public String path() {
            return path;
        }

        public String name() {
            return name;
        }

        public boolean directory() {
            return directory;
        }

        public long size() {
            return size;
        }
    }

    private final Map<String, FileEntry> byPath = new HashMap<String, FileEntry>();

    public void rebuild(List<FileEntry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("entries must not be null");
        }
        byPath.clear();
        for (int i = 0; i < entries.size(); i++) {
            upsert(entries.get(i));
        }
    }

    public void upsert(FileEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        byPath.put(entry.path(), entry);
    }

    public boolean remove(String path) {
        return byPath.remove(path) != null;
    }

    public FileEntry get(String path) {
        return byPath.get(path);
    }

    public boolean contains(String path) {
        return byPath.containsKey(path);
    }

    public int size() {
        return byPath.size();
    }

    /** Returns a mutable snapshot sorted by path so reports are deterministic. */
    public List<FileEntry> all() {
        List<FileEntry> result = new ArrayList<FileEntry>(byPath.values());
        Collections.sort(
                result,
                new Comparator<FileEntry>() {
                    @Override
                    public int compare(FileEntry left, FileEntry right) {
                        return left.path().compareTo(right.path());
                    }
                });
        return result;
    }
}
