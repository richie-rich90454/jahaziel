/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the app_user table. */
@Entity
@Table(name = "app_user")
public class AppUserEntity {

  /** Primary key of the user. */
  @Id
  @Column(name = "user_id")
  private UUID userId;

  /** External identity provider subject. */
  @Column(name = "external_id")
  private String externalId;

  /** Email address of the user. */
  @Column(name = "email")
  private String email;

  /** Human-readable display name. */
  @Column(name = "display_name")
  private String displayName;

  /** Creation timestamp. */
  @Column(name = "created_at")
  private Instant createdAt;

  /** Last login timestamp, null if never logged in. */
  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  /** Account status. */
  @Column(name = "status")
  private String status;

  /** Whether MFA enrollment is completed. */
  @Column(name = "mfa_enabled")
  private boolean mfaEnabled;

  /** Creates an empty instance for JPA. */
  protected AppUserEntity() {}

  /** Gets the primary key of the user. */
  public UUID getUserId() {
    return userId;
  }

  /** Sets the primary key of the user. */
  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  /** Gets the external identity provider subject. */
  public String getExternalId() {
    return externalId;
  }

  /** Sets the external identity provider subject. */
  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  /** Gets the email address of the user. */
  public String getEmail() {
    return email;
  }

  /** Sets the email address of the user. */
  public void setEmail(String email) {
    this.email = email;
  }

  /** Gets the human-readable display name. */
  public String getDisplayName() {
    return displayName;
  }

  /** Sets the human-readable display name. */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /** Gets the creation timestamp. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Sets the creation timestamp. */
  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  /** Gets the last login timestamp. */
  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  /** Sets the last login timestamp. */
  public void setLastLoginAt(Instant lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }

  /** Gets the account status. */
  public String getStatus() {
    return status;
  }

  /** Sets the account status. */
  public void setStatus(String status) {
    this.status = status;
  }

  /** Checks whether MFA enrollment is completed. */
  public boolean isMfaEnabled() {
    return mfaEnabled;
  }

  /** Sets whether MFA enrollment is completed. */
  public void setMfaEnabled(boolean mfaEnabled) {
    this.mfaEnabled = mfaEnabled;
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
    AppUserEntity that = (AppUserEntity) o;
    return Objects.equals(userId, that.userId);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(userId);
  }
}
