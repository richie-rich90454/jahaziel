/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.internal;

import dev.jahaziel.extension.api.ExtensionPolicyDoc;
import dev.jahaziel.extension.api.ExtensionPolicyService;
import dev.jahaziel.spi.Decision;
import dev.jahaziel.spi.ExtensionPolicy;
import dev.jahaziel.spi.ExtensionRequest;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates extension installs against stored policies. Most specific scope wins; version
 * constraints match exactly or by prefix before {@code .*}. Deny by default.
 */
public final class ExtensionDecider implements ExtensionPolicy {
  private final ExtensionPolicyService policies;

  /**
   * Creates the decider.
   *
   * @param policies the policy store
   */
  public ExtensionDecider(ExtensionPolicyService policies) {
    this.policies = Objects.requireNonNull(policies, "policies");
  }

  @Override
  public Decision decide(ExtensionRequest request) {
    Objects.requireNonNull(request, "request");
    String[][] scopes = {
      {"workspace", request.workspaceId().toString()},
      {"user", request.userId().toString()},
      {"global", null}
    };
    for (String[] scope : scopes) {
      List<ExtensionPolicyDoc> candidates =
          policies.listByScope(scope[0], scope[1]).stream()
              .filter(doc -> doc.extensionId().equals(request.extensionId()))
              .filter(doc -> versionMatches(doc.versionConstraint(), request.version()))
              .toList();
      if (!candidates.isEmpty()) {
        return decideScope(candidates);
      }
    }
    return Decision.BLOCK;
  }

  private static Decision decideScope(List<ExtensionPolicyDoc> candidates) {
    for (ExtensionPolicyDoc doc : candidates) {
      if (doc.action().equals("block")) {
        return Decision.BLOCK;
      }
    }
    for (ExtensionPolicyDoc doc : candidates) {
      if (doc.action().equals("require_approval")) {
        return Decision.REQUIRE_APPROVAL;
      }
    }
    if (candidates.stream().anyMatch(doc -> doc.action().equals("allow"))) {
      return Decision.ALLOW;
    }
    return Decision.BLOCK;
  }

  /**
   * Matches a version against a constraint. Null or blank constraints match everything; a trailing
   * {@code .*} matches prefixes; anything else must equal.
   *
   * @param constraint the stored constraint
   * @param version the requested version
   * @return true when the version is covered
   */
  static boolean versionMatches(String constraint, String version) {
    if (constraint == null || constraint.isBlank()) {
      return true;
    }
    if (version == null) {
      return false;
    }
    if (constraint.endsWith(".*")) {
      return version.startsWith(constraint.substring(0, constraint.length() - 2));
    }
    return constraint.equals(version);
  }
}
