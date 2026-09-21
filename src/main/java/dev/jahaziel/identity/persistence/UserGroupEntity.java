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

/** Persistence DTO for the user_group table. */
@Entity
@Table(name = "user_group")
public class UserGroupEntity {

  /** Composite key of the group membership. */
  @EmbeddedId private Id id;

  /** Timestamp when the user was added. */
  @Column(name = "added_at")
  private Instant addedAt;

  /** Creates an empty instance for JPA. */
  public UserGroupEntity() {}

  /** Gets the composite key of the group membership. */
  public Id getId() {
    return id;
  }

  /** Sets the composite key of the group membership. */
  public void setId(Id id) {
    this.id = id;
  }

  /** Gets the timestamp when the user was added. */
  public Instant getAddedAt() {
    return addedAt;
  }

  /** Sets the timestamp when the user was added. */
  public void setAddedAt(Instant addedAt) {
    this.addedAt = addedAt;
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
    UserGroupEntity that = (UserGroupEntity) o;
    return Objects.equals(id, that.id);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /** Composite key of the user_group table. */
  @Embeddable
  public static class Id implements Serializable {

    /** Serial version UID. */
    private static final long serialVersionUID = 1L;

    /** Member user id. */
    @Column(name = "user_id")
    private UUID userId;

    /** Group name. */
    @Column(name = "group_name")
    private String groupName;

    /** Creates an empty key for JPA. */
    public Id() {}

    /** Creates a key with all fields. */
    public Id(UUID userId, String groupName) {
      this.userId = userId;
      this.groupName = groupName;
    }

    /** Gets the member user id. */
    public UUID getUserId() {
      return userId;
    }

    /** Sets the member user id. */
    public void setUserId(UUID userId) {
      this.userId = userId;
    }

    /** Gets the group name. */
    public String getGroupName() {
      return groupName;
    }

    /** Sets the group name. */
    public void setGroupName(String groupName) {
      this.groupName = groupName;
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
      return Objects.equals(userId, that.userId) && Objects.equals(groupName, that.groupName);
    }

    /** Returns the hash code derived from all key fields. */
    @Override
    public int hashCode() {
      return Objects.hash(userId, groupName);
    }
  }
}
