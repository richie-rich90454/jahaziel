/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the workspace table. */
@Entity
@Table(name = "workspace")
public class WorkspaceEntity {

  /** Primary key of the workspace. */
  @Id
  @Column(name = "workspace_id")
  private UUID workspaceId;

  /** Owner user id. */
  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  /** Workspace name unique per owner. */
  @Column(name = "name")
  private String name;

  /** Lifecycle state. */
  @Column(name = "state")
  private String state;

  /** Last state transition timestamp. */
  @Column(name = "state_changed_at")
  private Instant stateChangedAt;

  /** Logical storage quota in bytes. */
  @Column(name = "logical_quota_bytes")
  private long logicalQuotaBytes;

  /** Inode quota. */
  @Column(name = "inode_quota")
  private long inodeQuota;

  /** Creation timestamp. */
  @Column(name = "created_at")
  private Instant createdAt;

  /** Soft-deletion timestamp, null when live. */
  @Column(name = "deleted_at")
  private Instant deletedAt;

  /** Creates an empty instance for JPA. */
  protected WorkspaceEntity() {}

  /** Gets the primary key of the workspace. */
  public UUID getWorkspaceId() {
    return workspaceId;
  }

  /** Sets the primary key of the workspace. */
  public void setWorkspaceId(UUID workspaceId) {
    this.workspaceId = workspaceId;
  }

  /** Gets the owner user id. */
  public UUID getOwnerUserId() {
    return ownerUserId;
  }

  /** Sets the owner user id. */
  public void setOwnerUserId(UUID ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  /** Gets the workspace name unique per owner. */
  public String getName() {
    return name;
  }

  /** Sets the workspace name unique per owner. */
  public void setName(String name) {
    this.name = name;
  }

  /** Gets the lifecycle state. */
  public String getState() {
    return state;
  }

  /** Sets the lifecycle state. */
  public void setState(String state) {
    this.state = state;
  }

  /** Gets the last state transition timestamp. */
  public Instant getStateChangedAt() {
    return stateChangedAt;
  }

  /** Sets the last state transition timestamp. */
  public void setStateChangedAt(Instant stateChangedAt) {
    this.stateChangedAt = stateChangedAt;
  }

  /** Gets the logical storage quota in bytes. */
  public long getLogicalQuotaBytes() {
    return logicalQuotaBytes;
  }

  /** Sets the logical storage quota in bytes. */
  public void setLogicalQuotaBytes(long logicalQuotaBytes) {
    this.logicalQuotaBytes = logicalQuotaBytes;
  }

  /** Gets the inode quota. */
  public long getInodeQuota() {
    return inodeQuota;
  }

  /** Sets the inode quota. */
  public void setInodeQuota(long inodeQuota) {
    this.inodeQuota = inodeQuota;
  }

  /** Gets the creation timestamp. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Sets the creation timestamp. */
  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  /** Gets the soft-deletion timestamp. */
  public Instant getDeletedAt() {
    return deletedAt;
  }

  /** Sets the soft-deletion timestamp. */
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
    WorkspaceEntity that = (WorkspaceEntity) o;
    return Objects.equals(workspaceId, that.workspaceId);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(workspaceId);
  }
}
