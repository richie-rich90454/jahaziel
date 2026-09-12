/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.internal;

import dev.jahaziel.policy.api.PolicyDoc;
import dev.jahaziel.policy.api.PolicyService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves the effective policy for a scope chain. Global documents apply first, then user, then
 * workspace; later documents deep-merge over earlier ones.
 */
public final class PolicyEvaluator {
  private final PolicyService policies;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Creates the evaluator.
   *
   * @param policies the policy store
   */
  public PolicyEvaluator(PolicyService policies) {
    this.policies = Objects.requireNonNull(policies, "policies");
  }

  /**
   * Resolves the effective policy JSON.
   *
   * @param userId the user scope id, empty for user-independent resolution
   * @param workspaceId the workspace scope id, empty for workspace-independent resolution
   * @return the merged JSON object, {@code {}} when no document exists
   */
  public String effectiveJson(Optional<String> userId, Optional<String> workspaceId) {
    Map<String, Object> merged = new LinkedHashMap<>();
    for (PolicyDoc doc : policies.listByScope("global", null)) {
      merge(merged, parse(doc.policyJson()));
    }
    if (userId.isPresent()) {
      for (PolicyDoc doc : policies.listByScope("user", userId.get())) {
        merge(merged, parse(doc.policyJson()));
      }
    }
    if (workspaceId.isPresent()) {
      for (PolicyDoc doc : policies.listByScope("workspace", workspaceId.get())) {
        merge(merged, parse(doc.policyJson()));
      }
    }
    try {
      return mapper.writeValueAsString(merged);
    } catch (tools.jackson.core.JacksonException failure) {
      throw new IllegalStateException("cannot encode effective policy", failure);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parse(String json) {
    try {
      Map<String, Object> parsed = mapper.readValue(json, Map.class);
      return parsed == null ? Map.of() : parsed;
    } catch (tools.jackson.core.JacksonException failure) {
      throw new IllegalArgumentException("policy document is not a JSON object: " + json, failure);
    }
  }

  @SuppressWarnings("unchecked")
  private static void merge(Map<String, Object> base, Map<String, Object> overlay) {
    for (Map.Entry<String, Object> entry : overlay.entrySet()) {
      Object current = base.get(entry.getKey());
      if (current instanceof Map && entry.getValue() instanceof Map) {
        merge((Map<String, Object>) current, (Map<String, Object>) entry.getValue());
      } else {
        base.put(entry.getKey(), entry.getValue());
      }
    }
  }
}
