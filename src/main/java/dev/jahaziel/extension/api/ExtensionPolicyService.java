/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.api;

import java.util.List;
import java.util.UUID;

/** Extension policies, stored per scope. */
public interface ExtensionPolicyService {
  /**
   * Creates an extension policy.
   *
   * @param scope the policy scope, for example {@code workspace}
   * @param scopeId the scope target id, or null for global scope
   * @param extensionId the extension identifier
   * @param action the enforcement action, for example {@code allow}
   * @param versionConstraint the version constraint, or null when unrestricted
   * @return the created document with a fresh id and the current timestamp
   * @throws IllegalArgumentException when scope, extensionId, or action is blank
   */
  ExtensionPolicyDoc create(
      String scope, String scopeId, String extensionId, String action, String versionConstraint);

  /**
   * Returns one extension policy.
   *
   * @param id the policy identifier
   * @return the extension policy document
   * @throws ExtensionPolicyNotFoundException when the policy does not exist
   */
  ExtensionPolicyDoc get(UUID id);

  /**
   * Lists extension policies for a scope. A null scope id lists global policies.
   *
   * @param scope the policy scope, for example {@code workspace}
   * @param scopeId the scope target id, or null for global scope
   * @return the matching documents
   * @throws IllegalArgumentException when scope is blank
   */
  List<ExtensionPolicyDoc> listByScope(String scope, String scopeId);

  /**
   * Deletes an extension policy.
   *
   * @param id the policy identifier
   * @throws ExtensionPolicyNotFoundException when the policy does not exist
   */
  void delete(UUID id);
}
