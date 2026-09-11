/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.audit.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the audit_event table. */
@Entity
@Table(name = "audit_event")
public class AuditEventEntity {

  /** Composite key of the audit event. */
  @EmbeddedId private Id id;

  /** Actor performing the action. */
  @Column(name = "actor")
  private String actor;

  /** Actor type. */
  @Column(name = "actor_type")
  private String actorType;

  /** Action performed. */
  @Column(name = "action")
  private String action;

  /** Action target, null when not applicable. */
  @Column(name = "target")
  private String target;

  /** Target type, null when not applicable. */
  @Column(name = "target_type")
  private String targetType;

  /** Action outcome. */
  @Column(name = "outcome")
  private String outcome;

  /** Source IP, null when not applicable. */
  @Column(name = "source_ip")
  private String sourceIp;

  /** Source node, null when not applicable. */
  @Column(name = "source_node")
  private String sourceNode;

  /** Event details as JSON. */
  @Column(name = "details", columnDefinition = "jsonb")
  private String details;

  /** Hash of the previous record, null for the first. */
  @Column(name = "prev_hash")
  private String prevHash;

  /** Hash of this record. */
  @Column(name = "hash")
  private String hash;

  /** Creates an empty instance for JPA and mapping. */
  public AuditEventEntity() {}

  /** Gets the composite key of the audit event. */
  public Id getId() {
    return id;
  }

  /** Sets the composite key of the audit event. */
  public void setId(Id id) {
    this.id = id;
  }

  /** Gets the actor performing the action. */
  public String getActor() {
    return actor;
  }

  /** Sets the actor performing the action. */
  public void setActor(String actor) {
    this.actor = actor;
  }

  /** Gets the actor type. */
  public String getActorType() {
    return actorType;
  }

  /** Sets the actor type. */
  public void setActorType(String actorType) {
    this.actorType = actorType;
  }

  /** Gets the action performed. */
  public String getAction() {
    return action;
  }

  /** Sets the action performed. */
  public void setAction(String action) {
    this.action = action;
  }

  /** Gets the action target. */
  public String getTarget() {
    return target;
  }

  /** Sets the action target. */
  public void setTarget(String target) {
    this.target = target;
  }

  /** Gets the target type. */
  public String getTargetType() {
    return targetType;
  }

  /** Sets the target type. */
  public void setTargetType(String targetType) {
    this.targetType = targetType;
  }

  /** Gets the action outcome. */
  public String getOutcome() {
    return outcome;
  }

  /** Sets the action outcome. */
  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  /** Gets the source IP. */
  public String getSourceIp() {
    return sourceIp;
  }

  /** Sets the source IP. */
  public void setSourceIp(String sourceIp) {
    this.sourceIp = sourceIp;
  }

  /** Gets the source node. */
  public String getSourceNode() {
    return sourceNode;
  }

  /** Sets the source node. */
  public void setSourceNode(String sourceNode) {
    this.sourceNode = sourceNode;
  }

  /** Gets the event details as JSON. */
  public String getDetails() {
    return details;
  }

  /** Sets the event details as JSON. */
  public void setDetails(String details) {
    this.details = details;
  }

  /** Gets the hash of the previous record. */
  public String getPrevHash() {
    return prevHash;
  }

  /** Sets the hash of the previous record. */
  public void setPrevHash(String prevHash) {
    this.prevHash = prevHash;
  }

  /** Gets the hash of this record. */
  public String getHash() {
    return hash;
  }

  /** Sets the hash of this record. */
  public void setHash(String hash) {
    this.hash = hash;
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
    AuditEventEntity that = (AuditEventEntity) o;
    return Objects.equals(id, that.id);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /** Composite key of the audit_event table. */
  @Embeddable
  public static class Id implements Serializable {

    /** Serial version UID. */
    private static final long serialVersionUID = 1L;

    /** Event id. */
    @Column(name = "event_id")
    private UUID eventId;

    /** Event timestamp. */
    @Column(name = "timestamp")
    private Instant timestamp;

    /** Creates an empty key for JPA. */
    protected Id() {}

    /** Creates a key with all fields. */
    public Id(UUID eventId, Instant timestamp) {
      this.eventId = eventId;
      this.timestamp = timestamp;
    }

    /** Gets the event id. */
    public UUID getEventId() {
      return eventId;
    }

    /** Sets the event id. */
    public void setEventId(UUID eventId) {
      this.eventId = eventId;
    }

    /** Gets the event timestamp. */
    public Instant getTimestamp() {
      return timestamp;
    }

    /** Sets the event timestamp. */
    public void setTimestamp(Instant timestamp) {
      this.timestamp = timestamp;
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
      return Objects.equals(eventId, that.eventId) && Objects.equals(timestamp, that.timestamp);
    }

    /** Returns the hash code derived from all key fields. */
    @Override
    public int hashCode() {
      return Objects.hash(eventId, timestamp);
    }
  }
}
