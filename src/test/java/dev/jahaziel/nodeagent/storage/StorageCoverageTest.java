/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jahaziel.nodeagent.sync.SyncException;
import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.QuotaBytes;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Constructor and parse branches of storage plus {@link SyncException}. */
class StorageCoverageTest {
  @Test
  void folderStorageRejectsBadRoots() {
    CommandRunner commands = Mockito.mock(CommandRunner.class);
    assertThrows(NullPointerException.class, () -> new FolderStorage(null, "/x"));
    assertThrows(IllegalArgumentException.class, () -> new FolderStorage(commands, null));
    assertThrows(IllegalArgumentException.class, () -> new FolderStorage(commands, "  "));
    assertThrows(IllegalArgumentException.class, () -> new FolderStorage(commands, ""));
  }

  @Test
  void folderStorageNulls() {
    CommandRunner commands = Mockito.mock(CommandRunner.class);
    FolderStorage storage = new FolderStorage(commands, "/x");
    UUID workspace = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    assertThrows(
        NullPointerException.class,
        () -> storage.prepare(null, owner, new ImageRef("i"), new QuotaBytes(1)));
    assertThrows(
        NullPointerException.class,
        () -> storage.prepare(workspace, null, new ImageRef("i"), new QuotaBytes(1)));
    assertThrows(
        NullPointerException.class,
        () -> storage.prepare(workspace, owner, null, new QuotaBytes(1)));
    assertThrows(
        NullPointerException.class,
        () -> storage.prepare(workspace, owner, new ImageRef("i"), null));
    assertThrows(NullPointerException.class, () -> storage.destroy(null));
    assertThrows(
        NullPointerException.class,
        () -> storage.snapshot(null, new dev.jahaziel.spi.SnapshotTag("t")));
    assertThrows(
        NullPointerException.class,
        () ->
            storage.snapshot(
                new dev.jahaziel.spi.WorkspaceHandle(workspace, Path.of("/h"), Path.of("/j")),
                null));
    assertThrows(NullPointerException.class, () -> storage.deleteSnapshot(null));
    assertThrows(
        NullPointerException.class,
        () ->
            storage.restore(
                null,
                new dev.jahaziel.spi.SnapshotHandle(
                    UUID.randomUUID(), Path.of("/s"), java.time.Instant.now())));
    assertThrows(NullPointerException.class, () -> FolderStorage.parseDu(null));
    assertThrows(StorageException.class, () -> FolderStorage.parseDu(""));
    assertThrows(NullPointerException.class, () -> new NioCommandRunner().run(null));
  }

  @Test
  void parseDuTrims() {
    assertEquals(11L, FolderStorage.parseDu("11\tdir"));
    assertEquals(0L, FolderStorage.parseDu("0\t."));
    assertThrows(StorageException.class, () -> FolderStorage.parseDu("nope"));
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
    assertThrows(StorageException.class, () -> runner.run(List.of("echo", "hi")));
    assertThrows(StorageException.class, () -> runner.run(List.of()));
    assertThrows(StorageException.class, () -> runner.run(List.of("mkdir", "x", "y")));
    assertThrows(StorageException.class, () -> runner.run(List.of("rm", "x", "y")));
    assertThrows(StorageException.class, () -> runner.run(List.of("mv", "only-two")));
    assertThrows(StorageException.class, () -> runner.run(List.of("cp", "-a", "only-three")));
    assertThrows(StorageException.class, () -> runner.run(List.of("chmod", "-R", "u+x", "/tmp")));
    assertThrows(StorageException.class, () -> runner.run(List.of("du", "x", "y")));
    assertTrue(true);
  }
}
