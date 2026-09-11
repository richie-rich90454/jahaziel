/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.workspace.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the workspace_acl table. */
@Entity
@Table(name = "workspace_acl")
public class WorkspaceAclEntity {

  /** Composite key of the ACL grant. */
  @EmbeddedId private Id id;

  /** Grant timestamp. */
  @Column(name = "granted_at")
  private Instant grantedAt;

  /** Grantor of the permission. */
  @Column(name = "granted_by")
  private String grantedBy;

  /** Creates an empty instance for JPA. */
  protected WorkspaceAclEntity() {}

  /** Gets the composite key of the ACL grant. */
  public Id getId() {
    return id;
  }

  /** Sets the composite key of the ACL grant. */
  public void setId(Id id) {
    this.id = id;
  }

  /** Gets the grant timestamp. */
  public Instant getGrantedAt() {
    return grantedAt;
  }

  /** Sets the grant timestamp. */
  public void setGrantedAt(Instant grantedAt) {
    this.grantedAt = grantedAt;
  }

  /** Gets the grantor of the permission. */
  public String getGrantedBy() {
    return grantedBy;
  }

  /** Sets the grantor of the permission. */
  public void setGrantedBy(String grantedBy) {
    this.grantedBy = grantedBy;
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
    WorkspaceAclEntity that = (WorkspaceAclEntity) o;
    return Objects.equals(id, that.id);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /** Composite key of the workspace_acl table. */
  @Embeddable
  public static class Id implements Serializable {

    /** Serial version UID. */
    private static final long serialVersionUID = 1L;

    /** Workspace id. */
    @Column(name = "workspace_id")
    private UUID workspaceId;

    /** Grantee user id. */
    @Column(name = "grantee_user_id")
    private UUID granteeUserId;

    /** Granted permission. */
    @Column(name = "permission")
    private String permission;

    /** Creates an empty key for JPA. */
    protected Id() {}

    /** Creates a key with all fields. */
    public Id(UUID workspaceId, UUID granteeUserId, String permission) {
      this.workspaceId = workspaceId;
      this.granteeUserId = granteeUserId;
      this.permission = permission;
    }

    /** Gets the workspace id. */
    public UUID getWorkspaceId() {
      return workspaceId;
    }

    /** Sets the workspace id. */
    public void setWorkspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
    }

    /** Gets the grantee user id. */
    public UUID getGranteeUserId() {
      return granteeUserId;
    }

    /** Sets the grantee user id. */
    public void setGranteeUserId(UUID granteeUserId) {
      this.granteeUserId = granteeUserId;
    }

    /** Gets the granted permission. */
    public String getPermission() {
      return permission;
    }

    /** Sets the granted permission. */
    public void setPermission(String permission) {
      this.permission = permission;
    }

    /** Compares by all key fields. */
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Id that = (Id) o;
      return Objects.equals(workspaceId, that.workspaceId)
          && Objects.equals(granteeUserId, that.granteeUserId)
          && Objects.equals(permission, that.permission);
    }

    /** Returns the hash code derived from all key fields. */
    @Override
    public int hashCode() {
      return Objects.hash(workspaceId, granteeUserId, permission);
    }
  }
}
