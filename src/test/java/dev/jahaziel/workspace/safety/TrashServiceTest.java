/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/** Trash keeps files workspace-scoped and restores them unchanged. */
final class TrashServiceTest {
    @Test
    void trashAndRestoreRoundTrip(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final TrashService trash = new TrashService(data, 60000L, 1000000L);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        Path file = SafetyPaths.home(data, workspace).resolve("notes.txt");
        SafetyFixtures.write(file, "hello");

        TrashEntry entry = trash.trash(workspace, "notes.txt", "user");
        assertTrue(!Files.exists(file));
        assertEquals(1, trash.list(workspace).size());
        assertEquals(
                "hello",
                SafetyFixtures.read(
                        SafetyPaths.trash(data, workspace)
                                .resolve("items")
                                .resolve(entry.getId())
                                .resolve("payload")
                                .resolve("notes.txt")));

        trash.restore(workspace, entry.getId());
        assertEquals("hello", SafetyFixtures.read(file));
        assertTrue(trash.list(workspace).isEmpty());
    }

    @Test
    void purgeAndEmptyDeletePermanently(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final TrashService trash = new TrashService(data, 60000L, 1000000L);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        SafetyFixtures.write(SafetyPaths.home(data, workspace).resolve("one.txt"), "1");
        SafetyFixtures.write(SafetyPaths.home(data, workspace).resolve("two.txt"), "2");
        TrashEntry one = trash.trash(workspace, "one.txt", "user");
        trash.trash(workspace, "two.txt", "user");
        trash.purge(workspace, one.getId());
        assertEquals(1, trash.list(workspace).size());
        trash.empty(workspace);
        assertEquals(0, trash.list(workspace).size());
        assertEquals(0L, trash.usedBytes(workspace));
    }

    @Test
    void expiredItemsArePurged(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final TrashService trash = new TrashService(data, 0L, 1000000L);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        SafetyFixtures.write(SafetyPaths.home(data, workspace).resolve("old.txt"), "old");
        trash.trash(workspace, "old.txt", "user");
        assertEquals(0, trash.list(workspace).size());
    }

    @Test
    void quotaIsEnforced(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final TrashService trash = new TrashService(data, 60000L, 1L);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        SafetyFixtures.write(SafetyPaths.home(data, workspace).resolve("big.txt"), "too big");
        assertThrows(
                IllegalStateException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        trash.trash(workspace, "big.txt", "user");
                    }
                });
    }
}
