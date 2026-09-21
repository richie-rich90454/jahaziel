/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.internal;

import dev.jahaziel.extension.api.ExtensionPolicyDoc;
import dev.jahaziel.extension.persistence.ExtensionPolicyEntity;

/**
 * Maps extension policy rows to documents and back. Pure static functions; the mapping lives at the
 * boundary, not in the domain.
 */
public final class ExtensionPolicyMapper {
  private ExtensionPolicyMapper() {}

  /**
   * Maps a document to its row.
   *
   * @param doc the extension policy document
   * @return the row
   */
  public static ExtensionPolicyEntity toEntity(ExtensionPolicyDoc doc) {
    ExtensionPolicyEntity row = new ExtensionPolicyEntity();
    row.setPolicyId(doc.id());
    row.setScope(doc.scope());
    row.setScopeId(doc.scopeId());
    row.setExtensionId(doc.extensionId());
    row.setAction(doc.action());
    row.setVersionConstraint(doc.versionConstraint());
    row.setUpdatedAt(doc.updatedAt());
    return row;
  }

  /**
   * Maps a row to its document.
   *
   * @param row the extension policy row
   * @return the document
   */
  public static ExtensionPolicyDoc toDomain(ExtensionPolicyEntity row) {
    return new ExtensionPolicyDoc(
        row.getPolicyId(),
        row.getScope(),
        row.getScopeId(),
        row.getExtensionId(),
        row.getAction(),
        row.getVersionConstraint(),
        row.getUpdatedAt());
  }
}
