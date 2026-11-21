/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.nodeagent.sync.SyncException;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.QuotaBytes;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Constructor and parse branches of storage plus {@link SyncException}. */
class StorageCoverageTest {
    @Test
    void folderStorageRejectsBadRoots() {
        CommandRunner commands = Mockito.mock(CommandRunner.class);
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new FolderStorage(null, "/x");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new FolderStorage(commands, null);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new FolderStorage(commands, "  ");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new FolderStorage(commands, "");
                    }
                });
    }

    @Test
    void folderStorageNulls() {
        CommandRunner commands = Mockito.mock(CommandRunner.class);
        FolderStorage storage = new FolderStorage(commands, "/x");
        UUID workspace = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        storage.prepare(null, owner, new ImageRef("i"), new QuotaBytes(1));
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        storage.prepare(workspace, null, new ImageRef("i"), new QuotaBytes(1));
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        storage.prepare(workspace, owner, null, new QuotaBytes(1));
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        storage.prepare(workspace, owner, new ImageRef("i"), null);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        storage.destroy(null);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        storage.snapshot(null, new dev.jahaziel.spi.SnapshotTag("t"));
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        storage.snapshot(
                                new dev.jahaziel.spi.WorkspaceHandle(
                                        workspace, Path.of("/h"), Path.of("/j")),
                                null);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        storage.deleteSnapshot(null);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        storage.restore(
                                null,
                                new dev.jahaziel.spi.SnapshotHandle(
                                        UUID.randomUUID(), Path.of("/s"), java.time.Instant.now()));
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        FolderStorage.parseDu(null);
                    }
                });
        assertThrows(
                StorageException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        FolderStorage.parseDu("");
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new NioCommandRunner().run(null);
                    }
                });
    }

    @Test
    void parseDuTrims() {
        assertEquals(11L, FolderStorage.parseDu("11\tdir"));
        assertEquals(0L, FolderStorage.parseDu("0\t."));
        assertThrows(
                StorageException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        FolderStorage.parseDu("nope");
                    }
                });
    }

    @Test
    void syncExceptionCarriesCause() {
        SyncException first = new SyncException("bad");
        assertEquals("bad", first.getMessage());
        java.io.IOException cause = new java.io.IOException("io");
        SyncException second = new SyncException("bad", cause);
        assertEquals(cause, second.getCause());
    }

    @Test
    void unsupportedNioShape() {
        NioCommandRunner runner = new NioCommandRunner();
        assertThrows(
                StorageException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        runner.run(List.of("echo", "hi"));
                    }
                });
        assertThrows(
                StorageException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        runner.run(List.of());
                    }
                });
        assertThrows(
                StorageException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        runner.run(List.of("mkdir", "x", "y"));
                    }
                });
        assertThrows(
                StorageException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        runner.run(List.of("rm", "x", "y"));
                    }
                });
        assertThrows(
                StorageException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        runner.run(List.of("mv", "only-two"));
                    }
                });
        assertThrows(
                StorageException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        runner.run(List.of("cp", "-a", "only-three"));
                    }
                });
        assertThrows(
                StorageException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        runner.run(List.of("chmod", "-R", "u+x", "/tmp"));
                    }
                });
        assertThrows(
                StorageException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        runner.run(List.of("du", "x", "y"));
                    }
                });
    }
}
