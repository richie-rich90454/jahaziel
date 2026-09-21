/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the policy table. */
@Entity
@Table(name = "policy")
public class PolicyEntity {

  /** Primary key of the policy. */
  @Id
  @Column(name = "policy_id")
  private UUID policyId;

  /** Policy scope. */
  @Column(name = "scope")
  private String scope;

  /** Scope target id, null for global scope. */
  @Column(name = "scope_id")
  private String scopeId;

  /** Policy document as JSON. */
  @Column(name = "policy_json")
  private String policyJson;

  /** Last update timestamp. */
  @Column(name = "updated_at")
  private Instant updatedAt;

  /** Updater of the policy. */
  @Column(name = "updated_by")
  private String updatedBy;

  /** Creates an empty instance for JPA and mapping. */
  public PolicyEntity() {}

  /** Gets the primary key of the policy. */
  public UUID getPolicyId() {
    return policyId;
  }

  /** Sets the primary key of the policy. */
  public void setPolicyId(UUID policyId) {
    this.policyId = policyId;
  }

  /** Gets the policy scope. */
  public String getScope() {
    return scope;
  }

  /** Sets the policy scope. */
  public void setScope(String scope) {
    this.scope = scope;
  }

  /** Gets the scope target id. */
  public String getScopeId() {
    return scopeId;
  }

  /** Sets the scope target id. */
  public void setScopeId(String scopeId) {
    this.scopeId = scopeId;
  }

  /** Gets the policy document as JSON. */
  public String getPolicyJson() {
    return policyJson;
  }

  /** Sets the policy document as JSON. */
  public void setPolicyJson(String policyJson) {
    this.policyJson = policyJson;
  }

  /** Gets the last update timestamp. */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /** Sets the last update timestamp. */
  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  /** Gets the updater of the policy. */
  public String getUpdatedBy() {
    return updatedBy;
  }

  /** Sets the updater of the policy. */
  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
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
    PolicyEntity that = (PolicyEntity) o;
    return Objects.equals(policyId, that.policyId);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(policyId);
  }
}
