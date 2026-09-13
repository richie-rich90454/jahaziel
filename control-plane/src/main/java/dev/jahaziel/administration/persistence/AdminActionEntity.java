/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.administration.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the admin_action table. */
@Entity
@Table(name = "admin_action")
public class AdminActionEntity {

  /** Primary key of the admin action. */
  @Id
  @Column(name = "action_id")
  private UUID actionId;

  /** Action timestamp. */
  @Column(name = "timestamp")
  private Instant timestamp;

  /** Acting admin user id. */
  @Column(name = "admin_user_id")
  private UUID adminUserId;

  /** Action performed. */
  @Column(name = "action")
  private String action;

  /** Action target, null when not applicable. */
  @Column(name = "target")
  private String target;

  /** Action details as JSON text. */
  @Column(name = "details")
  private String details;

  /** Reason for the action. */
  @Column(name = "reason")
  private String reason;

  /** Whether break-glass access was used. */
  @Column(name = "break_glass")
  private boolean breakGlass;

  /** Approver user id, null when not approved. */
  @Column(name = "approver_user_id")
  private UUID approverUserId;

  /** Creates an empty instance for JPA. */
  public AdminActionEntity() {}

  /** Gets the primary key of the admin action. */
  public UUID getActionId() {
    return actionId;
  }

  /** Sets the primary key of the admin action. */
  public void setActionId(UUID actionId) {
    this.actionId = actionId;
  }

  /** Gets the action timestamp. */
  public Instant getTimestamp() {
    return timestamp;
  }

  /** Sets the action timestamp. */
  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  /** Gets the acting admin user id. */
  public UUID getAdminUserId() {
    return adminUserId;
  }

  /** Sets the acting admin user id. */
  public void setAdminUserId(UUID adminUserId) {
    this.adminUserId = adminUserId;
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

  /** Gets the action details as JSON. */
  public String getDetails() {
    return details;
  }

  /** Sets the action details as JSON. */
  public void setDetails(String details) {
    this.details = details;
  }

  /** Gets the reason for the action. */
  public String getReason() {
    return reason;
  }

  /** Sets the reason for the action. */
  public void setReason(String reason) {
    this.reason = reason;
  }

  /** Checks whether break-glass access was used. */
  public boolean isBreakGlass() {
    return breakGlass;
  }

  /** Sets whether break-glass access was used. */
  public void setBreakGlass(boolean breakGlass) {
    this.breakGlass = breakGlass;
  }

  /** Gets the approver user id. */
  public UUID getApproverUserId() {
    return approverUserId;
  }

  /** Sets the approver user id. */
  public void setApproverUserId(UUID approverUserId) {
    this.approverUserId = approverUserId;
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
    AdminActionEntity that = (AdminActionEntity) o;
    return Objects.equals(actionId, that.actionId);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(actionId);
  }
}
