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

/** Persistence DTO for the bff_session table: one browser login, stored so it survives restarts. */
@Entity
@Table(name = "bff_session")
public class BffSessionEntity {

  /** Opaque session identifier, the cookie value. */
  @Id
  @Column(name = "session_id")
  private String sessionId;

  /** Owning user id. */
  @Column(name = "user_id")
  private UUID userId;

  /** Creation timestamp. */
  @Column(name = "created_at")
  private Instant createdAt;

  /** Absolute expiry. */
  @Column(name = "expires_at")
  private Instant expiresAt;

  /** Creates an empty instance for JPA. */
  public BffSessionEntity() {}

  /** Gets the session identifier. */
  public String getSessionId() {
    return sessionId;
  }

  /** Sets the session identifier. */
  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  /** Gets the owning user id. */
  public UUID getUserId() {
    return userId;
  }

  /** Sets the owning user id. */
  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  /** Gets the creation timestamp. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Sets the creation timestamp. */
  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  /** Gets the absolute expiry. */
  public Instant getExpiresAt() {
    return expiresAt;
  }

  /** Sets the absolute expiry. */
  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
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
    BffSessionEntity that = (BffSessionEntity) o;
    return Objects.equals(sessionId, that.sessionId);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(sessionId);
  }
}
