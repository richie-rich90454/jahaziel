/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the user_role table. */
@Entity
@Table(name = "user_role")
public class UserRoleEntity {

  /** Composite key of the role grant. */
  @EmbeddedId private Id id;

  /** Grant timestamp. */
  @Column(name = "granted_at")
  private Instant grantedAt;

  /** Grantor of the role. */
  @Column(name = "granted_by")
  private String grantedBy;

  /** Creates an empty instance for JPA. */
  public UserRoleEntity() {}

  /** Gets the composite key of the role grant. */
  public Id getId() {
    return id;
  }

  /** Sets the composite key of the role grant. */
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

  /** Gets the grantor of the role. */
  public String getGrantedBy() {
    return grantedBy;
  }

  /** Sets the grantor of the role. */
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
    UserRoleEntity that = (UserRoleEntity) o;
    return Objects.equals(id, that.id);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /** Composite key of the user_role table. */
  @Embeddable
  public static class Id implements Serializable {

    /** Serial version UID. */
    private static final long serialVersionUID = 1L;

    /** Owning user id. */
    @Column(name = "user_id")
    private UUID userId;

    /** Granted role name. */
    @Column(name = "role")
    private String role;

    /** Creates an empty key for JPA. */
    public Id() {}

    /** Creates a key with all fields. */
    public Id(UUID userId, String role) {
      this.userId = userId;
      this.role = role;
    }

    /** Gets the owning user id. */
    public UUID getUserId() {
      return userId;
    }

    /** Sets the owning user id. */
    public void setUserId(UUID userId) {
      this.userId = userId;
    }

    /** Gets the granted role name. */
    public String getRole() {
      return role;
    }

    /** Sets the granted role name. */
    public void setRole(String role) {
      this.role = role;
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
      return Objects.equals(userId, that.userId) && Objects.equals(role, that.role);
    }

    /** Returns the hash code derived from all key fields. */
    @Override
    public int hashCode() {
      return Objects.hash(userId, role);
    }
  }
}
