/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.domain;

import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Metadata of a point-in-time snapshot of a workspace home subvolume.
 *
 * @param id the snapshot identifier
 * @param workspaceId the workspace
 * @param tag the human tag, unique per workspace
 * @param kind how the snapshot was triggered
 * @param createdAt when the snapshot was taken
 * @param createdBy who triggered it, user id, system, or admin id
 * @param subvolumePath absolute path to the read-only subvolume
 * @param logicalBytes logical size in bytes
 * @param physicalBytes physical size in bytes
 * @param parentId the previous snapshot, empty for the first
 * @param manifestSha256 SHA-256 of the snapshot manifest
 */
public record SnapshotMetadata(
    SnapshotId id,
    WorkspaceId workspaceId,
    String tag,
    SnapshotKind kind,
    Instant createdAt,
    String createdBy,
    Path subvolumePath,
    long logicalBytes,
    long physicalBytes,
    Optional<SnapshotId> parentId,
    String manifestSha256) {
  public SnapshotMetadata {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(workspaceId, "workspaceId");
    if (tag == null || tag.isBlank()) {
      throw new IllegalArgumentException("tag must not be blank");
    }
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(createdAt, "createdAt");
    if (createdBy == null || createdBy.isBlank()) {
      throw new IllegalArgumentException("createdBy must not be blank");
    }
    Objects.requireNonNull(subvolumePath, "subvolumePath");
    if (logicalBytes < 0) {
      throw new IllegalArgumentException("logicalBytes must not be negative");
    }
    if (physicalBytes < 0) {
      throw new IllegalArgumentException("physicalBytes must not be negative");
    }
    Objects.requireNonNull(parentId, "parentId");
    if (manifestSha256 == null || manifestSha256.isBlank()) {
      throw new IllegalArgumentException("manifestSha256 must not be blank");
    }
  }

  /**
   * Creates a manual snapshot record with a fresh identifier.
   *
   * @param workspaceId the workspace
   * @param tag the human tag
   * @param createdBy who triggered it
   * @param subvolumePath absolute path to the read-only subvolume
   * @return the metadata
   */
  public static SnapshotMetadata manual(
      WorkspaceId workspaceId, String tag, String createdBy, Path subvolumePath) {
    return new SnapshotMetadata(
        new SnapshotId(UUID.randomUUID()),
        workspaceId,
        tag,
        SnapshotKind.MANUAL,
        Instant.now(),
        createdBy,
        subvolumePath,
        0,
        0,
        Optional.empty(),
        "pending");
  }
}
