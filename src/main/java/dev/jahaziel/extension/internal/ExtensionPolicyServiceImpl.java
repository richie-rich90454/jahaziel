/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.internal;

import dev.jahaziel.extension.api.ExtensionPolicyDoc;
import dev.jahaziel.extension.api.ExtensionPolicyNotFoundException;
import dev.jahaziel.extension.api.ExtensionPolicyService;
import dev.jahaziel.extension.persistence.ExtensionPolicyEntity;
import dev.jahaziel.extension.persistence.ExtensionPolicyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Database-backed extension policies. */
public final class ExtensionPolicyServiceImpl implements ExtensionPolicyService {
  private static final Set<String> SCOPES = Set.of("global", "user", "workspace");
  private static final Set<String> ACTIONS = Set.of("allow", "block", "require_approval");

  private final ExtensionPolicyRepository policies;

  /**
   * Creates the service.
   *
   * @param policies the extension policy rows
   */
  public ExtensionPolicyServiceImpl(ExtensionPolicyRepository policies) {
    this.policies = Objects.requireNonNull(policies, "policies");
  }

  @Override
  public ExtensionPolicyDoc create(
      String scope, String scopeId, String extensionId, String action, String versionConstraint) {
    if (!SCOPES.contains(scope)) {
      throw new IllegalArgumentException("scope must be global, user, or workspace");
    }
    if (scope.equals("global") && scopeId != null) {
      throw new IllegalArgumentException("global scope takes no scope id");
    }
    if (extensionId == null || extensionId.isBlank()) {
      throw new IllegalArgumentException("extensionId must not be blank");
    }
    if (!ACTIONS.contains(action)) {
      throw new IllegalArgumentException("action must be allow, block, or require_approval");
    }
    ExtensionPolicyDoc doc =
        new ExtensionPolicyDoc(
            UUID.randomUUID(),
            scope,
            scopeId,
            extensionId,
            action,
            versionConstraint,
            Instant.now());
    policies.save(ExtensionPolicyMapper.toEntity(doc));
    return doc;
  }

  @Override
  public ExtensionPolicyDoc get(UUID id) {
    Objects.requireNonNull(id, "id");
    ExtensionPolicyEntity row =
        policies.findById(id).orElseThrow(() -> new ExtensionPolicyNotFoundException(id));
    return ExtensionPolicyMapper.toDomain(row);
  }

  @Override
  public List<ExtensionPolicyDoc> listByScope(String scope, String scopeId) {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("scope must not be blank");
    }
    return policies.findAll().stream()
        .filter(row -> scopeMatch(row, scope, scopeId))
        .map(ExtensionPolicyMapper::toDomain)
        .toList();
  }

  @Override
  public void delete(UUID id) {
    Objects.requireNonNull(id, "id");
    ExtensionPolicyEntity row =
        policies.findById(id).orElseThrow(() -> new ExtensionPolicyNotFoundException(id));
    policies.delete(row);
  }

  /**
   * Matches a row against a scope. The repository has no finder yet, so callers filter in memory; a
   * derived finder replaces this when volumes grow. Null scope id means global scope: it matches
   * only rows whose scope id is also null.
   *
   * @param row the extension policy row
   * @param scope the policy scope
   * @param scopeId the scope target id, or null for global scope
   * @return true when the row belongs to the scope
   */
  private static boolean scopeMatch(ExtensionPolicyEntity row, String scope, String scopeId) {
    if (!scope.equals(row.getScope())) {
      return false;
    }
    return scopeId == null ? row.getScopeId() == null : scopeId.equals(row.getScopeId());
  }
}
