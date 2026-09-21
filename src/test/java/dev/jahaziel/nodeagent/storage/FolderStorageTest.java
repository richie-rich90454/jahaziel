/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.WorkspaceHandle;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Behavior of {@link FolderStorage} with a recording command runner. */
class FolderStorageTest {
  private CommandRunner commands;
  private FolderStorage storage;
  private UUID workspaceId;
  private UUID ownerId;

  @BeforeEach
  void setUp() {
    commands = mock(CommandRunner.class);
    storage = new FolderStorage(commands, "/var/lib/jahaziel");
    workspaceId = UUID.randomUUID();
    ownerId = UUID.randomUUID();
  }

  @Test
  void prepareCreatesJailAndHomeFolders() {
    WorkspaceHandle handle =
        storage.prepare(
            workspaceId, ownerId, new ImageRef("base-image:2026.09"), new QuotaBytes(4096));
    assertEquals(workspaceId, handle.workspaceId());
    assertEquals(Path.of("/var/lib/jahaziel/workspaces/" + ownerId + "/home"), handle.homePath());
    assertEquals(Path.of("/var/lib/jahaziel/jail/" + workspaceId), handle.jailPath());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(commands, org.mockito.Mockito.times(2)).run(captor.capture());
    List<List<String>> calls = captor.getAllValues();
    assertEquals(List.of("mkdir", "-p", handle.jailPath().toString()), calls.get(0));
    assertEquals(List.of("mkdir", "-p", handle.homePath().toString()), calls.get(1));
  }

  @Test
  void prepareKeepsExistingHomeWithinQuota() {
    when(commands.exists(any())).thenReturn(true);
    when(commands.run(any())).thenReturn("1234\t/home");
    storage.prepare(workspaceId, ownerId, new ImageRef("base-image:2026.09"), new QuotaBytes(4096));
    verify(commands, never()).run(List.of("mkdir", "-p", homePath().toString()));
    verify(commands).run(List.of("du", "-sb", homePath().toString()));
  }

  @Test
  void prepareOverQuotaThrows() {
    when(commands.exists(any())).thenReturn(true);
    when(commands.run(any())).thenReturn("9999\t/home");
    assertThrows(
        StorageException.class,
        () ->
            storage.prepare(
                workspaceId, ownerId, new ImageRef("base-image:2026.09"), new QuotaBytes(100)));
  }

  @Test
  void snapshotCopiesHomeReadOnly() {
    WorkspaceHandle handle = handle();
    SnapshotHandle snap = storage.snapshot(handle, new SnapshotTag("daily"));
    assertEquals(
        Path.of("/var/lib/jahaziel/workspaces/" + ownerId + "/snaps/" + workspaceId + "/daily"),
        snap.snapshotPath());
    verify(commands).run(List.of("mkdir", "-p", snap.snapshotPath().getParent().toString()));
    verify(commands)
        .run(List.of("cp", "-a", handle.homePath() + "/.", snap.snapshotPath().toString()));
    verify(commands).run(List.of("chmod", "-R", "a-w", snap.snapshotPath().toString()));
  }

  @Test
  void restoreSwapsHomes() {
    WorkspaceHandle handle = handle();
    SnapshotHandle snap = new SnapshotHandle(UUID.randomUUID(), Path.of("/snaps/x"), Instant.now());
    storage.restore(handle, snap);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(commands, org.mockito.Mockito.times(8)).run(captor.capture());
    List<List<String>> calls = captor.getAllValues();
    assertEquals(List.of("rm", "-rf", stagedOf(handle).toString()), calls.get(0));
    assertEquals(List.of("rm", "-rf", retiredOf(handle).toString()), calls.get(1));
    assertEquals("cp", calls.get(3).get(0));
    assertEquals("mv", calls.get(5).get(0));
    assertEquals("mv", calls.get(6).get(0));
    Path retired = retiredOf(handle);
    assertEquals(List.of("rm", "-rf", retired.toString()), calls.get(7));
  }

  private static Path stagedOf(WorkspaceHandle handle) {
    return handle.homePath().resolveSibling(handle.homePath().getFileName() + ".new");
  }

  private static Path retiredOf(WorkspaceHandle handle) {
    return handle.homePath().resolveSibling(handle.homePath().getFileName() + ".old");
  }

  @Test
  void deleteSnapshotUnlocksThenRemoves() {
    SnapshotHandle snap = new SnapshotHandle(UUID.randomUUID(), Path.of("/snaps/x"), Instant.now());
    storage.deleteSnapshot(snap);
    verify(commands).run(List.of("chmod", "-R", "u+w", snap.snapshotPath().toString()));
    verify(commands).run(List.of("rm", "-rf", snap.snapshotPath().toString()));
  }

  @Test
  void destroyRemovesJailOnly() {
    WorkspaceHandle handle = handle();
    storage.destroy(handle);
    verify(commands).run(List.of("rm", "-rf", handle.jailPath().toString()));
  }

  @Test
  void usageSumsHomeAndSnaps() {
    WorkspaceHandle handle = handle();
    when(commands.exists(any())).thenReturn(true);
    when(commands.run(any())).thenReturn("4096\t/x");
    var usage = storage.usage(handle);
    assertEquals(4096, usage.logicalBytes());
    assertEquals(4096, usage.physicalBytes());
    assertEquals(4096, usage.snapshotBytes());
  }

  @Test
  void usageMissingSnapsIsZero() {
    WorkspaceHandle handle = handle();
    when(commands.exists(any())).thenReturn(false);
    when(commands.run(any())).thenReturn("4096\t/x");
    var usage = storage.usage(handle);
    assertEquals(4096, usage.logicalBytes());
    assertEquals(0, usage.snapshotBytes());
    verify(commands, org.mockito.Mockito.times(1)).run(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void duNoiseThrows() {
    assertThrows(StorageException.class, () -> FolderStorage.parseDu("nothing here"));
    assertThrows(NullPointerException.class, () -> storage.usage(null));
  }

  private Path homePath() {
    return Path.of("/var/lib/jahaziel/workspaces/" + ownerId + "/home");
  }

  private WorkspaceHandle handle() {
    return new WorkspaceHandle(
        workspaceId, homePath(), Path.of("/var/lib/jahaziel/jail/" + workspaceId));
  }
}
