/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Polling reports added, modified, and deleted files relative to a persisted baseline. */
final class FileWatchServiceTest {
    @Test
    void detectsExternalChanges(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        final FileWatchService service = new FileWatchService(data);
        Path home = SafetyPaths.home(data, workspace);
        Path first = home.resolve("a.txt");
        SafetyFixtures.write(first, "one");

        List<WatchChange> initial = service.changesSince(workspace, 0L);
        assertEquals(1, initial.size());
        assertEquals(WatchChange.ADDED, initial.get(0).getKind());

        SafetyFixtures.write(first, "two");
        SafetyFixtures.write(home.resolve("b.txt"), "new");
        List<WatchChange> changes = service.changesSince(workspace, 0L);
        assertTrue(contains(changes, "a.txt", WatchChange.MODIFIED));
        assertTrue(contains(changes, "b.txt", WatchChange.ADDED));

        assertTrue(first.toFile().delete());
        List<WatchChange> afterDelete = service.changesSince(workspace, 0L);
        assertTrue(contains(afterDelete, "a.txt", WatchChange.DELETED));
    }

    @Test
    void timestampFiltersOldChanges(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        final FileWatchService service = new FileWatchService(data);
        SafetyFixtures.write(SafetyPaths.home(data, workspace).resolve("a.txt"), "one");
        service.changesSince(workspace, 0L);
        assertTrue(service.changesSince(workspace, Long.MAX_VALUE).isEmpty());
    }

    private static boolean contains(List<WatchChange> changes, String path, String kind) {
        for (int index = 0; index < changes.size(); index++) {
            WatchChange change = changes.get(index);
            if (change.getPath().equals(path) && change.getKind().equals(kind)) {
                return true;
            }
        }
        return false;
    }
}
