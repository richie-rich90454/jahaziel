/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

/** Behavior of {@link BtrfsStorage} with a recording command runner. */
class BtrfsStorageTest {
  private CommandRunner commands;
  private BtrfsStorage storage;
  private UUID workspaceId;
  private UUID ownerId;

  @BeforeEach
  void setUp() {
    commands = mock(CommandRunner.class);
    storage = new BtrfsStorage(commands, "/var/lib/jahaziel", "/var/lib/jahaziel/images");
    workspaceId = UUID.randomUUID();
    ownerId = UUID.randomUUID();
  }

  @Test
  void prepareReflinksBaseImageAndCreatesHome() {
    WorkspaceHandle handle =
        storage.prepare(workspaceId, ownerId, new ImageRef("base-image:2026.09"), new QuotaBytes(1));
    assertEquals(workspaceId, handle.workspaceId());
    assertEquals(
        Path.of("/var/lib/jahaziel/jail/" + workspaceId + "/rootfs.img"), handle.rootDiskPath());
    assertEquals(
        Path.of("/var/lib/jahaziel/workspaces/" + ownerId + "/home"), handle.homeSubvolumePath());
    var captor = ArgumentCaptor.forClass(List.class);
    verify(commands, org.mockito.Mockito.times(5)).run(captor.capture());
    List<List<String>> calls = captor.getAllValues();
    assertEquals(List.of("truncate", "-s", "20G", handle.rootDiskPath().toString()), calls.get(1));
    assertEquals("cp", calls.get(2).get(0));
    assertEquals("--reflink=always", calls.get(2).get(1));
  }

  @Test
  void snapshotTagsUnderOwnerSnaps() {
    WorkspaceHandle handle = handle();
    SnapshotHandle snap = storage.snapshot(handle, new SnapshotTag("daily"));
    assertEquals(
        Path.of("/var/lib/jahaziel/workspaces/" + ownerId + "/snaps/daily"),
        snap.subvolumePath());
    verify(commands)
        .run(
            List.of(
                "btrfs",
                "subvolume",
                "snapshot",
                "-r",
                handle.homeSubvolumePath().toString(),
                snap.subvolumePath().toString()));
  }

  @Test
  void restoreSwapsHomes() {
    WorkspaceHandle handle = handle();
    SnapshotHandle snap =
        new SnapshotHandle(UUID.randomUUID(), Path.of("/snaps/x"), Instant.now());
    storage.restore(handle, snap);
    var captor = ArgumentCaptor.forClass(List.class);
    verify(commands, org.mockito.Mockito.times(4)).run(captor.capture());
    assertEquals("mv", captor.getAllValues().get(1).get(0));
  }

  @Test
  void destroyRemovesJailOnly() {
    WorkspaceHandle handle = handle();
    storage.destroy(handle);
    verify(commands).run(List.of("rm", "-rf", handle.jailPath().toString()));
  }

  @Test
  void usageParsesQgroup() {
    WorkspaceHandle handle = handle();
    when(commands.run(any()))
        .thenReturn("qgroupid         rfer         excl\n0/261            4096           4096\n");
    var usage = storage.usage(handle);
    assertEquals(4096, usage.logicalBytes());
    assertEquals(4096, usage.physicalBytes());
  }

  @Test
  void qgroupNoiseThrows() {
    assertThrows(StorageException.class, () -> BtrfsStorage.parseQgroup("nothing here"));
    assertThrows(NullPointerException.class, () -> storage.usage(null));
  }

  @Test
  void prepareKeepsExistingHome() {
    when(commands.exists(any())).thenReturn(true);
    storage.prepare(workspaceId, ownerId, new ImageRef("base-image:2026.09"), new QuotaBytes(1));
    verify(commands, org.mockito.Mockito.times(4)).run(org.mockito.ArgumentMatchers.anyList());
  }

  private WorkspaceHandle handle() {
    return new WorkspaceHandle(
        workspaceId,
        Path.of("/var/lib/jahaziel/jail/" + workspaceId + "/rootfs.img"),
        Path.of("/var/lib/jahaziel/workspaces/" + ownerId + "/home"),
        Path.of("/var/lib/jahaziel/jail/" + workspaceId));
  }
}
