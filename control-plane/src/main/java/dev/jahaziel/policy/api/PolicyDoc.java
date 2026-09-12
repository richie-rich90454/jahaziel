/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable policy document. A null scope id means the policy applies globally.
 *
 * @param id the policy identifier
 * @param scope the policy scope, for example {@code workspace}
 * @param scopeId the scope target id, or null for global scope
 * @param policyJson the policy document as JSON
 * @param updatedAt the last update timestamp
 * @param updatedBy the last updater
 */
public record PolicyDoc(
    UUID id, String scope, String scopeId, String policyJson, Instant updatedAt, String updatedBy) {
  /**
   * Creates the document.
   *
   * @param id the policy identifier
   * @param scope the policy scope, for example {@code workspace}
   * @param scopeId the scope target id, or null for global scope
   * @param policyJson the policy document as JSON
   * @param updatedAt the last update timestamp
   * @param updatedBy the last updater
   * @throws NullPointerException when any non-nullable component is null
   * @throws IllegalArgumentException when scope, policyJson, or updatedBy is blank
   */
  public PolicyDoc {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(updatedAt, "updatedAt");
    Objects.requireNonNull(updatedBy, "updatedBy");
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("scope must not be blank");
    }
    if (policyJson == null || policyJson.isBlank()) {
      throw new IllegalArgumentException("policyJson must not be blank");
    }
    if (updatedBy.isBlank()) {
      throw new IllegalArgumentException("updatedBy must not be blank");
    }
  }
}
