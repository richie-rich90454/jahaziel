/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for the extension_policy table. */
@Entity
@Table(name = "extension_policy")
public class ExtensionPolicyEntity {

  /** Primary key of the extension policy. */
  @Id
  @Column(name = "policy_id")
  private UUID policyId;

  /** Policy scope. */
  @Column(name = "scope")
  private String scope;

  /** Scope target id, null for global scope. */
  @Column(name = "scope_id")
  private String scopeId;

  /** Extension id. */
  @Column(name = "extension_id")
  private String extensionId;

  /** Enforcement action. */
  @Column(name = "action")
  private String action;

  /** Version constraint, null when unrestricted. */
  @Column(name = "version_constraint")
  private String versionConstraint;

  /** Last update timestamp. */
  @Column(name = "updated_at")
  private Instant updatedAt;

  /** Creates an empty instance for JPA. */
  protected ExtensionPolicyEntity() {}

  /** Gets the primary key of the extension policy. */
  public UUID getPolicyId() {
    return policyId;
  }

  /** Sets the primary key of the extension policy. */
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

  /** Gets the extension id. */
  public String getExtensionId() {
    return extensionId;
  }

  /** Sets the extension id. */
  public void setExtensionId(String extensionId) {
    this.extensionId = extensionId;
  }

  /** Gets the enforcement action. */
  public String getAction() {
    return action;
  }

  /** Sets the enforcement action. */
  public void setAction(String action) {
    this.action = action;
  }

  /** Gets the version constraint. */
  public String getVersionConstraint() {
    return versionConstraint;
  }

  /** Sets the version constraint. */
  public void setVersionConstraint(String versionConstraint) {
    this.versionConstraint = versionConstraint;
  }

  /** Gets the last update timestamp. */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /** Sets the last update timestamp. */
  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
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
    ExtensionPolicyEntity that = (ExtensionPolicyEntity) o;
    return Objects.equals(policyId, that.policyId);
  }

  /** Returns the hash code derived from the primary key. */
  @Override
  public int hashCode() {
    return Objects.hash(policyId);
  }
}
