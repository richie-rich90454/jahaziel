/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

class AgentStoreTest {
    @TempDir Path temporary;

    @BeforeEach
    void useCanonicalTempDirectory() throws IOException {
        temporary = temporary.toRealPath();
    }

    @Test
    void everyWriteStaysUnderTheDataRoot() throws IOException {
        Path data = temporary.resolve("data");
        AgentStore store = new AgentStore(data, new ObjectMapper());
        Path target = store.containedChild(store.sessionRoot(), "user", "workspace", "index.json");

        store.write(target, "hello");

        assertTrue(Files.isRegularFile(target));
        final boolean[] outside = new boolean[] {false};
        Files.walkFileTree(
                data,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        if (!file.toAbsolutePath()
                                .normalize()
                                .startsWith(data.toAbsolutePath().normalize())) {
                            outside[0] = true;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        assertEquals(false, outside[0]);
    }

    @Test
    void rejectsTraversalInWorkspaceRelativePaths() {
        final AgentStore store = new AgentStore(temporary.resolve("data"), new ObjectMapper());
        final UUID workspace = UUID.randomUUID();

        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        store.resolveWorkspacePath(workspace, "../outside.txt");
                    }
                });
        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        store.resolveWorkspacePath(workspace, "src/../../outside.txt");
                    }
                });
    }

    @Test
    void rejectsAbsoluteAndDriveQualifiedPaths() {
        final AgentStore store = new AgentStore(temporary.resolve("data"), new ObjectMapper());
        final UUID workspace = UUID.randomUUID();

        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        store.resolveWorkspacePath(workspace, "/etc/passwd");
                    }
                });
        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        store.resolveWorkspacePath(workspace, "C:\\Windows\\win.ini");
                    }
                });
    }

    @Test
    void rejectsSymbolicLinksInAgentStorage() {
        Path data = temporary.resolve("data");
        AgentStore store = new AgentStore(data, new ObjectMapper());
        Path outside = temporary.resolve("outside.json");
        Path linked = store.containedChild(store.promptRoot(), "linked.json");
        assumeTrue(canLink(outside, linked));

        final Path target = linked;
        assertThrowsAgent(
                new Executable() {
                    @Override
                    public void execute() {
                        store.write(target, "value");
                    }
                });
    }

    private static boolean canLink(Path target, Path link) {
        try {
            Files.createDirectories(link.getParent());
            Files.writeString(target, "outside");
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException failure) {
            return false;
        }
    }

    private static void assertThrowsAgent(Executable executable) {
        try {
            executable.execute();
            fail("expected an AgentException");
        } catch (Throwable failure) {
            assertTrue(failure instanceof AgentException);
        }
    }
}
