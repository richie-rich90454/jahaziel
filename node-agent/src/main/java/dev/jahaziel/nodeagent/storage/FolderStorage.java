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
 * Copy-on-write workspace storage on Btrfs. Homes are per user; the root disk reflinks the base
 * image per workspace.
 */
public final class BtrfsStorage implements WorkspaceStorage {
  private final CommandRunner commands;
  private final String storageRoot;
  private final String imagesDir;

  /**
   * Creates the storage.
   *
   * @param commands the host command runner
   * @param storageRoot the storage root
   * @param imagesDir the base images directory
   */
  public BtrfsStorage(CommandRunner commands, String storageRoot, String imagesDir) {
    this.commands = Objects.requireNonNull(commands, "commands");
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    if (imagesDir == null || imagesDir.isBlank()) {
      throw new IllegalArgumentException("imagesDir must not be blank");
    }
    this.storageRoot = storageRoot;
    this.imagesDir = imagesDir;
  }

  @Override
  public WorkspaceHandle prepare(UUID workspaceId, UUID ownerId, ImageRef image, QuotaBytes quota) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(ownerId, "ownerId");
    Objects.requireNonNull(image, "image");
    Objects.requireNonNull(quota, "quota");
    Path jail = jailPath(workspaceId);
    Path disk = jail.resolve("rootfs.img");
    Path home = homePath(ownerId);
    commands.run(List.of("mkdir", "-p", jail.toString()));
    commands.run(List.of("truncate", "-s", "20G", disk.toString()));
    commands.run(List.of("cp", "--reflink=always", imageFile(image), disk.toString()));
    if (!commands.exists(home)) {
      commands.run(List.of("btrfs", "subvolume", "create", home.toString()));
    }
    commands.run(List.of("btrfs", "qgroup", "limit", quota.value() + "b", home.toString()));
    return new WorkspaceHandle(workspaceId, disk, home, jail);
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
    Path snap = snapsDir(handle).resolve(tag.value());
    commands.run(
        List.of(
            "btrfs",
            "subvolume",
            "snapshot",
            "-r",
            handle.homeSubvolumePath().toString(),
            snap.toString()));
    return new SnapshotHandle(UUID.randomUUID(), snap, Instant.now());
  }

  @Override
  public void deleteSnapshot(SnapshotHandle snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    commands.run(List.of("btrfs", "subvolume", "delete", snapshot.subvolumePath().toString()));
  }

  @Override
  public void restore(WorkspaceHandle handle, SnapshotHandle snapshot) {
    Objects.requireNonNull(handle, "handle");
    Objects.requireNonNull(snapshot, "snapshot");
    Path home = handle.homeSubvolumePath();
    Path staged = home.resolveSibling(home.getFileName() + ".new");
    Path retired = home.resolveSibling(home.getFileName() + ".old");
    commands.run(
        List.of(
            "btrfs",
            "subvolume",
            "snapshot",
            snapshot.subvolumePath().toString(),
            staged.toString()));
    commands.run(List.of("mv", home.toString(), retired.toString()));
    commands.run(List.of("mv", staged.toString(), home.toString()));
    commands.run(List.of("btrfs", "subvolume", "delete", retired.toString()));
  }

  @Override
  public StorageUsage usage(WorkspaceHandle handle) {
    Objects.requireNonNull(handle, "handle");
    String output =
        commands.run(
            List.of("btrfs", "qgroup", "show", "--raw", handle.homeSubvolumePath().toString()));
    long[] numbers = parseQgroup(output);
    return new StorageUsage(numbers[0], numbers[1], 0, numbers[0] - numbers[1]);
  }

  /**
   * Parses referenced and exclusive bytes from qgroup show output.
   *
   * @param output the command output
   * @return referenced and exclusive bytes
   */
  static long[] parseQgroup(String output) {
    String[] lines = output.trim().split("\n");
    String last = lines[lines.length - 1].trim();
    String[] fields = last.split("\\s+");
    if (fields.length < 3) {
      throw new StorageException("btrfs qgroup show", -1, output);
    }
    try {
      return new long[] {Long.parseLong(fields[1]), Long.parseLong(fields[2])};
    } catch (NumberFormatException failure) {
      throw new StorageException("btrfs qgroup show", -1, output);
    }
  }

  private Path jailPath(UUID workspaceId) {
    return Path.of(storageRoot, "jail", workspaceId.toString());
  }

  private Path homePath(UUID ownerId) {
    return Path.of(storageRoot, "workspaces", ownerId.toString(), "home");
  }

  private Path snapsDir(WorkspaceHandle handle) {
    return handle.homeSubvolumePath().getParent().resolve("snaps");
  }

  private String imageFile(ImageRef image) {
    // String concat, not Path: image refs contain colons (base-image:2026.09),
    // which are illegal in path segments on some platforms.
    return imagesDir + "/" + image.value() + ".ext4";
  }
}
