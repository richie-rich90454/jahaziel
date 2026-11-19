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

/** O(1) catalog lookup by the (os, arch, id) triple, including duplicate rejection. */
class CatalogIndexTest {
    private static List<CatalogIndex.Artifact> catalog() {
        List<CatalogIndex.Artifact> result = new ArrayList<CatalogIndex.Artifact>();
        result.add(new CatalogIndex.Artifact("linux", "x64", "open", "1", "https://x/open", "abc"));
        result.add(
                new CatalogIndex.Artifact("linux", "arm64", "open", "1", "https://x/open", "def"));
        result.add(new CatalogIndex.Artifact("linux", "x64", "ts", "2", "https://x/ts", "ghi"));
        return result;
    }

    @Test
    void findsByTripleOnly() {
        CatalogIndex index = new CatalogIndex(catalog());
        assertEquals(3, index.size());
        CatalogIndex.Artifact found = index.find("linux", "x64", "open");
        assertEquals("1", found.version());
        assertEquals("https://x/open", found.url());
        assertEquals("abc", found.sha256());
        assertTrue(index.contains("linux", "arm64", "open"));
        assertFalse(index.contains("windows", "x64", "open"));
        assertNull(index.find("linux", "arm64", "ts"));
    }

    @Test
    void sameOsAndIdButDifferentArchAreDistinct() {
        List<CatalogIndex.Artifact> source = catalog();
        CatalogIndex index = new CatalogIndex(source);
        CatalogIndex.Artifact x64 = index.find("linux", "x64", "open");
        CatalogIndex.Artifact arm64 = index.find("linux", "arm64", "open");
        assertSame(source.get(0), x64);
        assertFalse(x64 == arm64);
        assertEquals("def", arm64.sha256());
    }

    @Test
    void duplicateTripleRejected() {
        final List<CatalogIndex.Artifact> entries = catalog();
        entries.add(new CatalogIndex.Artifact("linux", "x64", "open", "9", "https://x/z", "zzz"));
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new CatalogIndex(entries);
                    }
                });
    }

    @Test
    void emptyIdentityRejected() {
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new CatalogIndex.Artifact("", "x64", "open", "1", "url", "hash");
                    }
                });
    }
}
