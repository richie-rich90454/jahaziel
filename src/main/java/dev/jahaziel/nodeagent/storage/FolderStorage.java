/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.nodeagent.storage;

import dev.jahaziel.spi.ImageRef;
import dev.jahaziel.spi.QuotaBytes;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.StorageUsage;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain-folder workspace storage. Homes, snapshots, and jails are ordinary directories on any
 * filesystem: no disk images, no partitions, no filesystem-specific features. Snapshots are
 * recursive copies frozen read-only; quotas are enforced logically against du measurements.
 */
public final class FolderStorage implements WorkspaceStorage {
  private final CommandRunner commands;
  private final String storageRoot;

  /**
   * Creates the storage.
   *
   * @param commands the host command runner
   * @param storageRoot the storage root
   */
  public FolderStorage(CommandRunner commands, String storageRoot) {
    this.commands = Objects.requireNonNull(commands, "commands");
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    this.storageRoot = storageRoot;
  }

  @Override
  public WorkspaceHandle prepare(UUID workspaceId, UUID ownerId, ImageRef image, QuotaBytes quota) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(image, "image");
    Objects.requireNonNull(quota, "quota");
    Path jail = jailPath(workspaceId);
    Path home = homePath(ownerId);
    commands.run(List.of("mkdir", "-p", jail.toString()));
    if (commands.exists(home)) {
      enforceQuota(home, quota);
    } else {
      commands.run(List.of("mkdir", "-p", home.toString()));
    }
    return new WorkspaceHandle(workspaceId, home, jail);
  }

  @Override
  public void destroy(WorkspaceHandle handle) {
    Objects.requireNonNull(handle, "handle");
    commands.run(List.of("rm", "-rf", handle.jailPath().toString()));
  }

  @Override
  public SnapshotHandle snapshot(WorkspaceHandle handle, SnapshotTag tag) {
    Objects.requireNonNull(handle, "handle");
    Objects.requireNonNull(tag, "tag");
    Path snaps = snapsDir(handle);
    Path snap = snaps.resolve(tag.value());
    commands.run(List.of("mkdir", "-p", snaps.toString()));
    commands.run(List.of("cp", "-a", contentsOf(handle.homePath()), snap.toString()));
    commands.run(List.of("chmod", "-R", "a-w", snap.toString()));
    return new SnapshotHandle(UUID.randomUUID(), snap, Instant.now());
  }

  @Override
  public void deleteSnapshot(SnapshotHandle snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    commands.run(List.of("chmod", "-R", "u+w", snapshot.snapshotPath().toString()));
    commands.run(List.of("rm", "-rf", snapshot.snapshotPath().toString()));
  }

  @Override
  public void restore(WorkspaceHandle handle, SnapshotHandle snapshot) {
    Objects.requireNonNull(handle, "handle");
    Objects.requireNonNull(snapshot, "snapshot");
    Path home = handle.homePath();
    Path staged = home.resolveSibling(home.getFileName() + ".new");
    Path retired = home.resolveSibling(home.getFileName() + ".old");
    commands.run(List.of("rm", "-rf", staged.toString()));
    commands.run(List.of("rm", "-rf", retired.toString()));
    commands.run(List.of("mkdir", "-p", staged.toString()));
    commands.run(List.of("cp", "-a", contentsOf(snapshot.snapshotPath()), staged.toString()));
    commands.run(List.of("chmod", "-R", "u+w", staged.toString()));
    commands.run(List.of("mv", home.toString(), retired.toString()));
    commands.run(List.of("mv", staged.toString(), home.toString()));
    commands.run(List.of("rm", "-rf", retired.toString()));
  }

  @Override
  public StorageUsage usage(WorkspaceHandle handle) {
    Objects.requireNonNull(handle, "handle");
    long homeBytes = directoryBytes(handle.homePath());
    Path snaps = snapsDir(handle);
    long snapshotBytes = commands.exists(snaps) ? directoryBytes(snaps) : 0;
    return new StorageUsage(homeBytes, homeBytes, snapshotBytes, 0);
  }

  /**
   * Parses the byte count from du output.
   *
   * @param output the command output
   * @return the leading byte count
   */
  static long parseDu(String output) {
    String first = output.trim().split("\\s+")[0];
    try {
      return Long.parseLong(first);
    } catch (NumberFormatException failure) {
      throw new StorageException("du -sb", -1, output);
    }
  }

  private void enforceQuota(Path home, QuotaBytes quota) {
    long bytes = directoryBytes(home);
    if (bytes > quota.value()) {
      throw new StorageException("quota exceeded for " + home, -1, bytes + " > " + quota.value());
    }
  }

  private long directoryBytes(Path dir) {
    return parseDu(commands.run(List.of("du", "-sb", dir.toString())));
  }

  private Path jailPath(UUID workspaceId) {
    return Path.of(storageRoot, "jail", workspaceId.toString());
  }

  private Path homePath(UUID ownerId) {
    return Path.of(storageRoot, "workspaces", ownerId.toString(), "home");
  }

  private Path snapsDir(WorkspaceHandle handle) {
    return handle.homePath().getParent().resolve("snaps").resolve(handle.workspaceId().toString());
  }

  private static String contentsOf(Path dir) {
    // String concat, not Path: the trailing "/." copies the contents, not the directory itself.
    return dir + "/.";
  }
}
