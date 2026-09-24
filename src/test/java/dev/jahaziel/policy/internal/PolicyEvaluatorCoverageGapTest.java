/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.policy.api.PolicyDoc;
import dev.jahaziel.policy.api.PolicyService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class PolicyEvaluatorCoverageGapTest {
  private static PolicyDoc doc(String scope, String scopeId, String json) {
    return new PolicyDoc(UUID.randomUUID(), scope, scopeId, json, Instant.now(), "op");
  }

  @Test
  void nullDocumentJsonMergesAsEmpty() {
    var svc = mock(PolicyService.class);
    when(svc.listByScope("global", null)).thenReturn(List.of(doc("global", null, "null")));
    assertEquals("{}", new PolicyEvaluator(svc).effectiveJson(Optional.empty(), Optional.empty()));
  }

  @Test
  void mixedNestingOverwritesBothWays() {
    var svc = mock(PolicyService.class);
    when(svc.listByScope("global", null))
        .thenReturn(List.of(doc("global", null, "{\"n\":{\"x\":1},\"m\":1}")));
    when(svc.listByScope("user", "u1")).thenReturn(List.of(doc("user", "u1", "{\"n\":5}")));
    when(svc.listByScope("workspace", "w1"))
        .thenReturn(List.of(doc("workspace", "w1", "{\"m\":{\"y\":2}}")));
    String merged = new PolicyEvaluator(svc).effectiveJson(Optional.of("u1"), Optional.of("w1"));
    assertTrue(merged.contains("\"n\":5"));
    assertTrue(merged.contains("\"y\":2"));
  }

  @Test
  void encodeFailureSurfacesAsIllegalState() throws Exception {
    var svc = mock(PolicyService.class);
    when(svc.listByScope(any(), any())).thenReturn(List.of());
    PolicyEvaluator evaluator = new PolicyEvaluator(svc);
    ObjectMapper broken = mock(ObjectMapper.class);
    when(broken.writeValueAsString(any())).thenThrow(mock(JacksonException.class));
    Field mapper = PolicyEvaluator.class.getDeclaredField("mapper");
    mapper.setAccessible(true);
    mapper.set(evaluator, broken);
    assertThrows(
        IllegalStateException.class,
        () -> evaluator.effectiveJson(Optional.empty(), Optional.empty()));
  }
}
