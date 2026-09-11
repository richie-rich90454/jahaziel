/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.snapshot.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the snapshot table. */
@Entity
@Table(name = "snapshot")
public class SnapshotEntity {

  /** Primary key of the snapshot. */
  @Id
  @Column(name = "snapshot_id")
  private UUID snapshotId;

  /** Owning workspace id. */
  @Column(name = "workspace_id")
  private UUID workspaceId;

  /** Tag unique per workspace. */
  @Column(name = "tag")
  private String tag;

  /** Snapshot kind. */
  @Column(name = "kind")
  private String kind;

  /** Creation timestamp. */
  @Column(name = "created_at")
  private Instant createdAt;

  /** Creator of the snapshot. */
  @Column(name = "created_by")
  private String createdBy;

  /** Subvolume path on the host. */
  @Column(name = "btrfs_subvolume_path")
  private String btrfsSubvolumePath;

  /** Logical size in bytes. */
  @Column(name = "logical_bytes")
  private long logicalBytes;

  /** Physical size in bytes. */
  @Column(name = "physical_bytes")
  private long physicalBytes;

  /** Parent snapshot id, null for a root snapshot. */
  @Column(name = "parent_snapshot_id")
  private UUID parentSnapshotId;

  /** SHA-256 of the snapshot manifest. */
  @Column(name = "sha256_of_manifest")
  private String sha256OfManifest;

  /** Deletion timestamp, null while live. */
  @Column(name = "deleted_at")
  private Instant deletedAt;

  /** Creates an empty instance for JPA. */
  public SnapshotEntity() {}

  /** Gets the primary key of the snapshot. */
  public UUID getSnapshotId() {
    return snapshotId;
  }

  /** Sets the primary key of the snapshot. */
  public void setSnapshotId(UUID snapshotId) {
    this.snapshotId = snapshotId;
  }

  /** Gets the owning workspace id. */
  public UUID getWorkspaceId() {
    return workspaceId;
  }

  /** Sets the owning workspace id. */
  public void setWorkspaceId(UUID workspaceId) {
    this.workspaceId = workspaceId;
  }

  /** Gets the tag unique per workspace. */
  public String getTag() {
    return tag;
  }

  /** Sets the tag unique per workspace. */
  public void setTag(String tag) {
    this.tag = tag;
  }

  /** Gets the snapshot kind. */
  public String getKind() {
    return kind;
  }

  /** Sets the snapshot kind. */
  public void setKind(String kind) {
    this.kind = kind;
  }

  /** Gets the creation timestamp. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Sets the creation timestamp. */
  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  /** Gets the creator of the snapshot. */
  public String getCreatedBy() {
    return createdBy;
  }

  /** Sets the creator of the snapshot. */
  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  /** Gets the subvolume path on the host. */
  public String getBtrfsSubvolumePath() {
    return btrfsSubvolumePath;
  }

  /** Sets the subvolume path on the host. */
  public void setBtrfsSubvolumePath(String btrfsSubvolumePath) {
    this.btrfsSubvolumePath = btrfsSubvolumePath;
  }

  /** Gets the logical size in bytes. */
  public long getLogicalBytes() {
    return logicalBytes;
  }

  /** Sets the logical size in bytes. */
  public void setLogicalBytes(long logicalBytes) {
    this.logicalBytes = logicalBytes;
  }

  /** Gets the physical size in bytes. */
  public long getPhysicalBytes() {
    return physicalBytes;
  }

  /** Sets the physical size in bytes. */
  public void setPhysicalBytes(long physicalBytes) {
    this.physicalBytes = physicalBytes;
  }

  /** Gets the parent snapshot id. */
  public UUID getParentSnapshotId() {
    return parentSnapshotId;
  }

  /** Sets the parent snapshot id. */
  public void setParentSnapshotId(UUID parentSnapshotId) {
    this.parentSnapshotId = parentSnapshotId;
  }

  /** Gets the SHA-256 of the snapshot manifest. */
  public String getSha256OfManifest() {
    return sha256OfManifest;
  }

  /** Sets the SHA-256 of the snapshot manifest. */
  public void setSha256OfManifest(String sha256OfManifest) {
    this.sha256OfManifest = sha256OfManifest;
  }

  /** Gets the deletion timestamp. */
  public Instant getDeletedAt() {
    return deletedAt;
  }

  /** Sets the deletion timestamp. */
  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  /** Compares by class and primary key. */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SnapshotEntity that = (SnapshotEntity) o;
    return Objects.equals(snapshotId, that.snapshotId);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(snapshotId);
  }
}
