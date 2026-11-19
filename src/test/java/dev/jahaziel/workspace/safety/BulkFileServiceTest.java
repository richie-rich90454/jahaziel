/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.workspace.internal.WorkspaceFilesServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Bulk operations reuse the workspace file API and enforce hard path and byte limits. */
final class BulkFileServiceTest {
    @Test
    void copiesMovesAndDeletes(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final BulkFileService service =
                new BulkFileService(
                        new WorkspaceFilesServiceImpl(data), new OperationHistory(data), data);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        Path home = SafetyPaths.home(data, workspace);
        SafetyFixtures.write(home.resolve("a.txt"), "a");
        SafetyFixtures.write(home.resolve("b.txt"), "b");

        List<String> one = new ArrayList<String>();
        one.add("a.txt");
        assertEquals(
                1,
                service.bulk(workspace, BulkFileService.COPY, one, "copy.txt", "user")
                        .getApplied());
        assertEquals("a", SafetyFixtures.read(home.resolve("copy.txt")));

        List<String> move = new ArrayList<String>();
        move.add("b.txt");
        assertEquals(
                1,
                service.bulk(workspace, BulkFileService.MOVE, move, "moved.txt", "user")
                        .getApplied());
        assertTrue(!Files.exists(home.resolve("b.txt")));
        assertEquals("b", SafetyFixtures.read(home.resolve("moved.txt")));

        List<String> remove = new ArrayList<String>();
        remove.add("copy.txt");
        assertEquals(
                1,
                service.bulk(workspace, BulkFileService.DELETE, remove, null, "user").getApplied());
        assertTrue(!Files.exists(home.resolve("copy.txt")));
    }

    @Test
    void rejectsTooManyPaths(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final BulkFileService service =
                new BulkFileService(
                        new WorkspaceFilesServiceImpl(data), new OperationHistory(data), data);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        final List<String> many = new ArrayList<String>();
        for (int index = 0; index < 101; index++) {
            String path = "f" + index + ".txt";
            SafetyFixtures.write(SafetyPaths.home(data, workspace).resolve(path), "x");
            many.add(path);
        }
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.bulk(workspace, BulkFileService.DELETE, many, null, "user");
                    }
                });
    }

    @Test
    void rejectsMissingDestination(@TempDir Path temp) {
        final String data = temp.resolve("data").toString();
        final BulkFileService service =
                new BulkFileService(
                        new WorkspaceFilesServiceImpl(data), new OperationHistory(data), data);
        final dev.jahaziel.workspace.domain.WorkspaceId workspace = SafetyFixtures.workspace();
        SafetyFixtures.write(SafetyPaths.home(data, workspace).resolve("a.txt"), "a");
        final List<String> paths = new ArrayList<String>();
        paths.add("a.txt");
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        service.bulk(workspace, BulkFileService.COPY, paths, null, "user");
                    }
                });
    }
}
