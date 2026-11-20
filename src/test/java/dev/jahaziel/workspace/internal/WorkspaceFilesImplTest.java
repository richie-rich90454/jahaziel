/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.workspace.api.WorkspaceFileConflictException;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

class WorkspaceFilesImplTest {
    @TempDir Path temporary;

    @Test
    void workspacesHaveIndependentHomes() {
        WorkspaceFilesImpl first = new WorkspaceFilesImpl(temporary.toString(), id());
        WorkspaceFilesImpl second = new WorkspaceFilesImpl(temporary.toString(), id());

        first.save("README.md", "first", null, true);

        assertEquals("first", first.read("README.md").content());
        assertTrue(second.list(".").isEmpty());
    }

    @Test
    void savesRequireCurrentVersionAndReturnReplacementVersion() {
        WorkspaceFilesImpl files = new WorkspaceFilesImpl(temporary.toString(), id());
        dev.jahaziel.workspace.api.WorkspaceFiles.Document original =
                files.save("main.ts", "const value = 1;", null, true);

        assertThrows(
                WorkspaceFileConflictException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.save("main.ts", "const value = 2;", "stale", false);
                    }
                });
        dev.jahaziel.workspace.api.WorkspaceFiles.Document updated =
                files.save("main.ts", "const value = 2;", original.version(), false);

        assertEquals("const value = 2;", updated.content());
        assertNotEquals(original.version(), updated.version());
        assertEquals(updated.version(), files.read("main.ts").version());
    }

    @Test
    void concurrentSavesWithOneVersionAllowOnlyOneReplacement() throws Exception {
        WorkspaceFilesImpl files = new WorkspaceFilesImpl(temporary.toString(), id());
        dev.jahaziel.workspace.api.WorkspaceFiles.Document original =
                files.save("main.ts", "const value = 1;", null, true);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<Boolean> first =
                    executor.submit(
                            new java.util.concurrent.Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    return saveAfter(
                                            start,
                                            ready,
                                            files,
                                            original.version(),
                                            "const value = 2;");
                                }
                            });
            java.util.concurrent.Future<Boolean> second =
                    executor.submit(
                            new java.util.concurrent.Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    return saveAfter(
                                            start,
                                            ready,
                                            files,
                                            original.version(),
                                            "const value = 3;");
                                }
                            });
            ready.await();
            start.countDown();

            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
    }

    private static boolean saveAfter(
            CountDownLatch start,
            CountDownLatch ready,
            WorkspaceFilesImpl files,
            String version,
            String content)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            files.save("main.ts", content, version, false);
            return true;
        } catch (WorkspaceFileConflictException conflict) {
            return false;
        }
    }

    @Test
    void createOnlySaveCannotReplaceAnExistingFile() {
        WorkspaceFilesImpl files = new WorkspaceFilesImpl(temporary.toString(), id());
        files.save("main.py", "print('safe')", null, true);

        assertThrows(
                WorkspaceFileConflictException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.save("main.py", "print('overwrite')", null, true);
                    }
                });
        assertEquals("print('safe')", files.read("main.py").content());
    }

    @Test
    void concurrentCreateOnlySavesCannotReplaceTheWinningFile() throws Exception {
        WorkspaceFilesImpl files = new WorkspaceFilesImpl(temporary.toString(), id());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<Boolean> first =
                    executor.submit(
                            new java.util.concurrent.Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    return createAfter(start, ready, files, "first");
                                }
                            });
            java.util.concurrent.Future<Boolean> second =
                    executor.submit(
                            new java.util.concurrent.Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    return createAfter(start, ready, files, "second");
                                }
                            });
            ready.await();
            start.countDown();

            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            assertTrue(List.of("first", "second").contains(files.read("shared.txt").content()));
        }
    }

    private static boolean createAfter(
            CountDownLatch start, CountDownLatch ready, WorkspaceFilesImpl files, String content)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            files.save("shared.txt", content, null, true);
            return true;
        } catch (WorkspaceFileConflictException conflict) {
            return false;
        }
    }

    @Test
    void boundsReadsAndDetectsOversizedFilesDuringSaveConflictChecks() throws IOException {
        WorkspaceId workspaceId = id();
        WorkspaceFilesImpl files = new WorkspaceFilesImpl(temporary.toString(), workspaceId);
        files.createDirectories(".");
        Path home = Path.of(WorkspacePaths.homePath(temporary.toString(), workspaceId));
        Files.writeString(home.resolve("large.txt"), "x".repeat(2 * 1024 * 1024 + 1));

        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.read("large.txt");
                    }
                });
        assertThrows(
                WorkspaceFileConflictException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.save("large.txt", "replacement", "stale-version", false);
                    }
                });
    }

    @Test
    void rejectsTraversalAndNonTextDocuments() throws IOException {
        WorkspaceId workspaceId = id();
        WorkspaceFilesImpl files = new WorkspaceFilesImpl(temporary.toString(), workspaceId);
        files.createDirectories(".");
        Path home = Path.of(WorkspacePaths.homePath(temporary.toString(), workspaceId));

        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.read("../outside.txt");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.read("C:/outside.txt");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.save("bad\\path", "x", null, true);
                    }
                });

        Files.write(home.resolve("binary.dat"), new byte[] {0, 1, 2});
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.read("binary.dat");
                    }
                });
        assertEquals("binary.dat", files.download("binary.dat").fileName());
        assertEquals(3, files.download("binary.dat").size());
    }

    @Test
    void searchFindsTextAndNamesButSkipsGeneratedDirectories() {
        WorkspaceFilesImpl files = new WorkspaceFilesImpl(temporary.toString(), id());
        files.createDirectories("src");
        files.createDirectories("node_modules/example");
        files.save("src/main.ts", "const answer = 'needle';\n", null, true);
        files.save("node_modules/example/index.js", "needle", null, true);
        files.save("src/needle-notes.txt", "nothing relevant", null, true);

        List<dev.jahaziel.workspace.api.WorkspaceFiles.SearchResult> matches =
                files.search("NEEDLE", 20);

        assertEquals(2, matches.size());
        boolean mainMatch = false;
        boolean nameMatch = false;
        for (dev.jahaziel.workspace.api.WorkspaceFiles.SearchResult match : matches) {
            if (match.path().equals("src/main.ts") && match.line() == 1) mainMatch = true;
            if (match.path().equals("src/needle-notes.txt") && match.line() == 0) nameMatch = true;
        }
        assertTrue(mainMatch);
        assertTrue(nameMatch);
        assertEquals(1, files.search("needle", 1).size());
    }

    @Test
    void searchRejectsInvalidQueryAndResultLimit() {
        WorkspaceFilesImpl files = new WorkspaceFilesImpl(temporary.toString(), id());

        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.search("  ", 10);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.search("needle", 0);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.search("needle", 101);
                    }
                });
    }

    @Test
    void rejectsSymlinkEscapeAndDoesNotListExternalTarget() throws IOException {
        WorkspaceId workspaceId = id();
        WorkspaceFilesImpl files = new WorkspaceFilesImpl(temporary.toString(), workspaceId);
        files.createDirectories(".");
        Path home = Path.of(WorkspacePaths.homePath(temporary.toString(), workspaceId));
        Path outside = temporary.resolve("outside.txt");
        Path link = home.resolve("external-link");
        Files.writeString(outside, "secret");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException unavailable) {
            Assumptions.abort(
                    "symbolic links are unavailable on this host: " + unavailable.getMessage());
        }

        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.read("external-link");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.download("external-link");
                    }
                });
        assertFalse(hasExternalLink(files));
    }

    @Test
    void renameAndDeleteStayInsideWorkspace() {
        WorkspaceFilesImpl files = new WorkspaceFilesImpl(temporary.toString(), id());
        files.save("old.txt", "value", null, true);
        files.createDirectories("nested");

        files.rename("old.txt", "nested/new.txt");

        assertEquals("value", files.read("nested/new.txt").content());
        files.delete("nested/new.txt");
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() throws Throwable {
                        files.read("nested/new.txt");
                    }
                });
    }

    private static WorkspaceId id() {
        return new WorkspaceId(UUID.randomUUID());
    }

    private static boolean hasExternalLink(WorkspaceFilesImpl files) {
        for (dev.jahaziel.workspace.api.WorkspaceFiles.Entry entry : files.list(".")) {
            if (entry.name().equals("external-link")) return true;
        }
        return false;
    }
}
