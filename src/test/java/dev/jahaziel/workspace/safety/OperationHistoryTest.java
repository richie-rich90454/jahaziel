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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Undo and redo must replay operations exactly once and survive a service restart. */
public final class OperationHistoryTest {
    @Test
    void undoRestoresOldContentAndRedoReapplies(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final OperationHistory history = new OperationHistory(data);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        Path file = SafetyPaths.home(data, workspace).resolve("a.txt");
        SafetyFixtures.write(file, "old");
        final byte[] old = "old".getBytes(StandardCharsets.UTF_8);
        final byte[] changed = "new".getBytes(StandardCharsets.UTF_8);
        history.recordWrite(workspace, "a.txt", old, changed, SafetyFixtures.USER);
        SafetyFixtures.write(file, "new");

        assertEquals("new", SafetyFixtures.read(file));
        List<OperationRecord> undone = history.undo(workspace);
        assertEquals(1, undone.size());
        assertEquals("old", SafetyFixtures.read(file));
        assertTrue(history.undo(workspace).isEmpty());
        assertEquals("old", SafetyFixtures.read(file));

        List<OperationRecord> redone = history.redo(workspace);
        assertEquals(1, redone.size());
        assertEquals("new", SafetyFixtures.read(file));
        assertTrue(history.redo(workspace).isEmpty());
    }

    @Test
    void deleteAndCreateAreReversible(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final OperationHistory history = new OperationHistory(data);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        Path file = SafetyPaths.home(data, workspace).resolve("gone.txt");
        SafetyFixtures.write(file, "payload");
        history.recordDelete(
                workspace, "gone.txt", "payload".getBytes(StandardCharsets.UTF_8), "user");
        history.undo(workspace);
        assertEquals("payload", SafetyFixtures.read(file));
        history.redo(workspace);
        assertTrue(!Files.exists(file));
    }

    @Test
    void journalSurvivesRestart(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        Path file = SafetyPaths.home(data, workspace).resolve("persist.txt");
        SafetyFixtures.write(file, "before");
        OperationHistory first = new OperationHistory(data);
        first.recordWrite(
                workspace,
                "persist.txt",
                "before".getBytes(StandardCharsets.UTF_8),
                "after".getBytes(StandardCharsets.UTF_8),
                "user");
        SafetyFixtures.write(file, "after");
        OperationHistory second = new OperationHistory(data);
        assertEquals(1, second.list(workspace).size());
        second.undo(workspace);
        assertEquals("before", SafetyFixtures.read(file));
    }

    @Test
    void gitMetadataIsRejected(@TempDir Path temp) {
        final OperationHistory history = new OperationHistory(temp.resolve("data").toString());
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        history.recordWrite(workspace, ".git/config", null, new byte[0], "user");
                    }
                });
    }
}
