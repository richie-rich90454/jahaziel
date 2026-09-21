/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.internal;

import dev.jahaziel.policy.api.PolicyDoc;
import dev.jahaziel.policy.persistence.PolicyEntity;

/**
 * Maps policy rows to documents and back. Pure static functions; the mapping lives at the boundary,
 * not in the domain.
 */
public final class PolicyMapper {
  private PolicyMapper() {}

  /**
   * Maps a document to its row.
   *
   * @param doc the policy document
   * @return the row
   */
  public static PolicyEntity toEntity(PolicyDoc doc) {
    PolicyEntity row = new PolicyEntity();
    row.setPolicyId(doc.id());
    row.setScope(doc.scope());
    row.setScopeId(doc.scopeId());
    row.setPolicyJson(doc.policyJson());
    row.setUpdatedAt(doc.updatedAt());
    row.setUpdatedBy(doc.updatedBy());
    return row;
  }

  /**
   * Maps a row to its document.
   *
   * @param row the policy row
   * @return the document
   */
  public static PolicyDoc toDomain(PolicyEntity row) {
    return new PolicyDoc(
        row.getPolicyId(),
        row.getScope(),
        row.getScopeId(),
        row.getPolicyJson(),
        row.getUpdatedAt(),
        row.getUpdatedBy());
  }
}
