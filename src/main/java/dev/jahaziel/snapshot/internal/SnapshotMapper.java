/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.internal;

import dev.jahaziel.snapshot.api.SnapshotKinds;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.snapshot.persistence.SnapshotEntity;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/** Maps snapshot rows to metadata and back. Pure static functions. */
public final class SnapshotMapper {
  private SnapshotMapper() {}

  /**
   * Maps metadata to its row.
   *
   * @param metadata the metadata
   * @return the row
   */
  public static SnapshotEntity toEntity(SnapshotMetadata metadata) {
    SnapshotEntity row = new SnapshotEntity();
    row.setSnapshotId(metadata.id().value());
    row.setWorkspaceId(metadata.workspaceId().value());
    row.setTag(metadata.tag());
    row.setKind(SnapshotKinds.toString(metadata.kind()));
    row.setCreatedAt(metadata.createdAt());
    row.setCreatedBy(metadata.createdBy());
    row.setSnapshotPath(metadata.snapshotPath().toString());
    row.setLogicalBytes(metadata.logicalBytes());
    row.setPhysicalBytes(metadata.physicalBytes());
    row.setParentSnapshotId(null);
    if (metadata.parentId().isPresent()) {
      row.setParentSnapshotId(metadata.parentId().get().value());
    }
    row.setSha256OfManifest(metadata.manifestSha256());
    return row;
  }

  /**
   * Rebuilds metadata from its row.
   *
   * @param row the row
   * @return the metadata
   */
  public static SnapshotMetadata toDomain(SnapshotEntity row) {
    UUID parent = row.getParentSnapshotId();
    return new SnapshotMetadata(
        new SnapshotId(row.getSnapshotId()),
        new WorkspaceId(row.getWorkspaceId()),
        row.getTag(),
        SnapshotKinds.fromString(row.getKind()),
        row.getCreatedAt(),
        row.getCreatedBy(),
        Path.of(row.getSnapshotPath()),
        row.getLogicalBytes(),
        row.getPhysicalBytes(),
        parent == null ? Optional.empty() : Optional.of(new SnapshotId(parent)),
        row.getSha256OfManifest());
  }
}
