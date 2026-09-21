/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.api;

import java.util.List;
import java.util.UUID;

/** Policies, stored per scope. */
public interface PolicyService {
  /**
   * Creates a policy.
   *
   * @param scope the policy scope, for example {@code workspace}
   * @param scopeId the scope target id, or null for global scope
   * @param policyJson the policy document as JSON
   * @param updatedBy the updater
   * @return the created document with a fresh id and the current timestamp
   * @throws IllegalArgumentException when scope, policyJson, or updatedBy is blank
   */
  PolicyDoc create(String scope, String scopeId, String policyJson, String updatedBy);

  /**
   * Returns one policy.
   *
   * @param id the policy identifier
   * @return the policy document
   * @throws PolicyNotFoundException when the policy does not exist
   */
  PolicyDoc get(UUID id);

  /**
   * Lists policies for a scope. A null scope id lists global policies.
   *
   * @param scope the policy scope, for example {@code workspace}
   * @param scopeId the scope target id, or null for global scope
   * @return the matching documents
   * @throws IllegalArgumentException when scope is blank
   */
  List<PolicyDoc> listByScope(String scope, String scopeId);

  /**
   * Deletes a policy.
   *
   * @param id the policy identifier
   * @throws PolicyNotFoundException when the policy does not exist
   */
  void delete(UUID id);
}
