/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable extension policy. A null scope id means the policy applies globally; a null version
 * constraint means versions are unrestricted.
 *
 * @param id the policy identifier
 * @param scope the policy scope, for example {@code workspace}
 * @param scopeId the scope target id, or null for global scope
 * @param extensionId the extension identifier
 * @param action the enforcement action, for example {@code allow}
 * @param versionConstraint the version constraint, or null when unrestricted
 * @param updatedAt the last update timestamp
 */
public record ExtensionPolicyDoc(
    UUID id,
    String scope,
    String scopeId,
    String extensionId,
    String action,
    String versionConstraint,
    Instant updatedAt) {
  /**
   * Creates the document.
   *
   * @param id the policy identifier
   * @param scope the policy scope, for example {@code workspace}
   * @param scopeId the scope target id, or null for global scope
   * @param extensionId the extension identifier
   * @param action the enforcement action, for example {@code allow}
   * @param versionConstraint the version constraint, or null when unrestricted
   * @param updatedAt the last update timestamp
   * @throws NullPointerException when any non-nullable component is null
   * @throws IllegalArgumentException when scope, extensionId, or action is blank
   */
  public ExtensionPolicyDoc {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("scope must not be blank");
    }
    if (extensionId == null || extensionId.isBlank()) {
      throw new IllegalArgumentException("extensionId must not be blank");
    }
    if (action == null || action.isBlank()) {
      throw new IllegalArgumentException("action must not be blank");
    }
  }
}
