/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.internal;

import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.snapshot.api.SnapshotNotFoundException;
import dev.jahaziel.snapshot.api.SnapshotService;
import dev.jahaziel.snapshot.domain.SnapshotId;
import dev.jahaziel.snapshot.domain.SnapshotKind;
import dev.jahaziel.snapshot.domain.SnapshotMetadata;
import dev.jahaziel.snapshot.persistence.SnapshotEntity;
import dev.jahaziel.snapshot.persistence.SnapshotRepository;
import dev.jahaziel.spi.ActorType;
import dev.jahaziel.spi.Outcome;
import dev.jahaziel.spi.SnapshotHandle;
import dev.jahaziel.spi.SnapshotTag;
import dev.jahaziel.spi.WorkspaceHandle;
import dev.jahaziel.spi.WorkspaceStorage;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.internal.WorkspacePaths;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Database-backed snapshots over workspace storage. Sizes and manifest hashes stay zero and empty
 * until verification measures them.
 */
public final class SnapshotServiceImpl implements SnapshotService {
  private final SnapshotRepository snapshots;
  private final WorkspaceService workspaces;
  private final WorkspaceStorage storage;
  private final AuditService audit;
  private final String storageRoot;

  /**
   * Creates the service.
   *
   * @param snapshots the snapshot rows
   * @param workspaces the workspace lookup
   * @param storage the workspace storage
   * @param audit the audit log
   * @param storageRoot the storage root
   */
  public SnapshotServiceImpl(
      SnapshotRepository snapshots,
      WorkspaceService workspaces,
      WorkspaceStorage storage,
      AuditService audit,
      String storageRoot) {
    this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.storage = Objects.requireNonNull(storage, "storage");
    this.audit = Objects.requireNonNull(audit, "audit");
    if (storageRoot == null || storageRoot.isBlank()) {
      throw new IllegalArgumentException("storageRoot must not be blank");
    }
    this.storageRoot = storageRoot;
  }

  @Override
  public SnapshotMetadata create(
      WorkspaceId workspaceId, String tag, SnapshotKind kind, String createdBy) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    if (tag == null || tag.isBlank()) {
      throw new IllegalArgumentException("tag must not be blank");
    }
    Objects.requireNonNull(kind, "kind");
    if (createdBy == null || createdBy.isBlank()) {
      throw new IllegalArgumentException("createdBy must not be blank");
    }
    boolean tagTaken =
        snapshots.findByWorkspaceId(workspaceId.value()).stream()
            .anyMatch(row -> row.getTag().equals(tag));
    if (tagTaken) {
      throw new IllegalArgumentException("snapshot tag already exists: " + tag);
    }
    Workspace workspace = workspaces.get(workspaceId);
    var handle = handleOf(workspace);
    SnapshotHandle snapshot = storage.snapshot(handle, new SnapshotTag(tag));
    Optional<SnapshotId> parent =
        snapshots.findByWorkspaceId(workspaceId.value()).stream()
            .max((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()))
            .map(row -> new SnapshotId(row.getSnapshotId()));
    SnapshotMetadata metadata =
        new SnapshotMetadata(
            new SnapshotId(UUID.randomUUID()),
            workspaceId,
            tag,
            kind,
            Instant.now(),
            createdBy,
            snapshot.snapshotPath(),
            0,
            0,
            parent,
            "");
    snapshots.save(SnapshotMapper.toEntity(metadata));
    audit.append(
        new AuditRecord(
            UUID.randomUUID(),
            Instant.now(),
            createdBy,
            ActorType.USER,
            "snapshot.create",
            Optional.of(workspaceId.value().toString()),
            Optional.of("snapshot"),
            Outcome.SUCCESS,
            Optional.empty(),
            Optional.empty(),
            Map.of("tag", tag),
            Optional.empty(),
            ""));
    return metadata;
  }

  @Override
  public List<SnapshotMetadata> list(WorkspaceId workspaceId) {
    Objects.requireNonNull(workspaceId, "workspaceId");
    return snapshots.findByWorkspaceId(workspaceId.value()).stream()
        .map(SnapshotMapper::toDomain)
        .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
        .toList();
  }

  @Override
  public void restore(WorkspaceId workspaceId, SnapshotId snapshotId) {
    SnapshotEntity row = rowOf(snapshotId);
    if (!row.getWorkspaceId().equals(workspaceId.value())) {
      throw new IllegalArgumentException("snapshot does not belong to workspace");
    }
    Workspace workspace = workspaces.get(workspaceId);
    storage.restore(
        handleOf(workspace),
        new SnapshotHandle(
            row.getSnapshotId(), Path.of(row.getSnapshotPath()), row.getCreatedAt()));
    audit.append(
        new AuditRecord(
            UUID.randomUUID(),
            Instant.now(),
            "system",
            ActorType.SYSTEM,
            "snapshot.restore",
            Optional.of(workspaceId.value().toString()),
            Optional.of("snapshot"),
            Outcome.SUCCESS,
            Optional.empty(),
            Optional.empty(),
            Map.of("tag", row.getTag()),
            Optional.empty(),
            ""));
  }

  @Override
  public void delete(SnapshotId snapshotId) {
    SnapshotEntity row = rowOf(snapshotId);
    storage.deleteSnapshot(
        new SnapshotHandle(
            row.getSnapshotId(), Path.of(row.getSnapshotPath()), row.getCreatedAt()));
    snapshots.delete(row);
  }

  private SnapshotEntity rowOf(SnapshotId snapshotId) {
    return snapshots
        .findById(snapshotId.value())
        .orElseThrow(() -> new SnapshotNotFoundException(snapshotId));
  }

  private WorkspaceHandle handleOf(Workspace workspace) {
    return new WorkspaceHandle(
        workspace.id().value(),
        Path.of(WorkspacePaths.homePath(storageRoot, workspace.owner())),
        Path.of(WorkspacePaths.jailPath(storageRoot, workspace.id())));
  }
}
