/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.internal;

import dev.jahaziel.policy.api.PolicyDoc;
import dev.jahaziel.policy.api.PolicyNotFoundException;
import dev.jahaziel.policy.api.PolicyService;
import dev.jahaziel.policy.persistence.PolicyEntity;
import dev.jahaziel.policy.persistence.PolicyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Database-backed policies. */
public final class PolicyServiceImpl implements PolicyService {
  private final PolicyRepository policies;

  /**
   * Creates the service.
   *
   * @param policies the policy rows
   */
  public PolicyServiceImpl(PolicyRepository policies) {
    this.policies = Objects.requireNonNull(policies, "policies");
  }

  @Override
  public PolicyDoc create(String scope, String scopeId, String policyJson, String updatedBy) {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("scope must not be blank");
    }
    if (policyJson == null || policyJson.isBlank()) {
      throw new IllegalArgumentException("policyJson must not be blank");
    }
    if (updatedBy == null || updatedBy.isBlank()) {
      throw new IllegalArgumentException("updatedBy must not be blank");
    }
    PolicyDoc doc =
        new PolicyDoc(UUID.randomUUID(), scope, scopeId, policyJson, Instant.now(), updatedBy);
    policies.save(PolicyMapper.toEntity(doc));
    return doc;
  }

  @Override
  public PolicyDoc get(UUID id) {
    Objects.requireNonNull(id, "id");
    PolicyEntity row = policies.findById(id).orElseThrow(() -> new PolicyNotFoundException(id));
    return PolicyMapper.toDomain(row);
  }

  @Override
  public List<PolicyDoc> listByScope(String scope, String scopeId) {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("scope must not be blank");
    }
    return policies.findAll().stream()
        .filter(row -> scopeMatch(row, scope, scopeId))
        .map(PolicyMapper::toDomain)
        .toList();
  }

  @Override
  public void delete(UUID id) {
    Objects.requireNonNull(id, "id");
    PolicyEntity row = policies.findById(id).orElseThrow(() -> new PolicyNotFoundException(id));
    policies.delete(row);
  }

  /**
   * Matches a row against a scope. The repository has no finder yet, so callers filter in memory; a
   * derived finder replaces this when volumes grow. Null scope id means global scope: it matches
   * only rows whose scope id is also null.
   *
   * @param row the policy row
   * @param scope the policy scope
   * @param scopeId the scope target id, or null for global scope
   * @return true when the row belongs to the scope
   */
  private static boolean scopeMatch(PolicyEntity row, String scope, String scopeId) {
    if (!scope.equals(row.getScope())) {
      return false;
    }
    return scopeId == null ? row.getScopeId() == null : scopeId.equals(row.getScopeId());
  }
}
