/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.networking.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the workspace_port table. */
@Entity
@Table(name = "workspace_port")
public class WorkspacePortEntity {

  /** Composite key of the port registration. */
  @EmbeddedId private Id id;

  /** Transport protocol. */
  @Column(name = "protocol")
  private String protocol;

  /** Registration timestamp. */
  @Column(name = "registered_at")
  private Instant registeredAt;

  /** Last-seen timestamp. */
  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  /** Creates an empty instance for JPA. */
  public WorkspacePortEntity() {}

  /** Gets the composite key of the port registration. */
  public Id getId() {
    return id;
  }

  /** Sets the composite key of the port registration. */
  public void setId(Id id) {
    this.id = id;
  }

  /** Gets the transport protocol. */
  public String getProtocol() {
    return protocol;
  }

  /** Sets the transport protocol. */
  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  /** Gets the registration timestamp. */
  public Instant getRegisteredAt() {
    return registeredAt;
  }

  /** Sets the registration timestamp. */
  public void setRegisteredAt(Instant registeredAt) {
    this.registeredAt = registeredAt;
  }

  /** Gets the last-seen timestamp. */
  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  /** Sets the last-seen timestamp. */
  public void setLastSeenAt(Instant lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
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
    WorkspacePortEntity that = (WorkspacePortEntity) o;
    return Objects.equals(id, that.id);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /** Composite key of the workspace_port table. */
  @Embeddable
  public static class Id implements Serializable {

    /** Serial version UID. */
    private static final long serialVersionUID = 1L;

    /** Owning workspace id. */
    @Column(name = "workspace_id")
    private UUID workspaceId;

    /** Registered port number. */
    @Column(name = "port")
    private int port;

    /** Creates an empty key for JPA. */
    public Id() {}

    /** Creates a key with all fields. */
    public Id(UUID workspaceId, int port) {
      this.workspaceId = workspaceId;
      this.port = port;
    }

    /** Gets the owning workspace id. */
    public UUID getWorkspaceId() {
      return workspaceId;
    }

    /** Sets the owning workspace id. */
    public void setWorkspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
    }

    /** Gets the registered port number. */
    public int getPort() {
      return port;
    }

    /** Sets the registered port number. */
    public void setPort(int port) {
      this.port = port;
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
      return port == that.port && Objects.equals(workspaceId, that.workspaceId);
    }

    /** Returns the hash code derived from all key fields. */
    @Override
    public int hashCode() {
      return Objects.hash(workspaceId, port);
    }
  }
}
