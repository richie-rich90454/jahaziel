/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.extension.api.ExtensionPolicyDoc;
import dev.jahaziel.extension.api.ExtensionPolicyService;
import dev.jahaziel.spi.Decision;
import dev.jahaziel.spi.ExtensionRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link ExtensionDecider} with a stubbed policy store. */
class ExtensionDeciderTest {
  private ExtensionPolicyService policies;
  private ExtensionDecider decider;

  @BeforeEach
  void setUp() {
    policies = mock(ExtensionPolicyService.class);
    decider = new ExtensionDecider(policies);
  }

  @Test
  void allowsListedExtension() {
    when(policies.listByScope("global", null))
        .thenReturn(List.of(doc("allow", null)));
    assertEquals(Decision.ALLOW, decider.decide(request("1.2.3")));
  }

  @Test
  void blockBeatsAllow() {
    when(policies.listByScope("global", null))
        .thenReturn(List.of(doc("allow", null), doc("block", null)));
    assertEquals(Decision.BLOCK, decider.decide(request("1.2.3")));
  }

  @Test
  void deniesByDefault() {
    when(policies.listByScope("global", null)).thenReturn(List.of());
    assertEquals(Decision.BLOCK, decider.decide(request("1.2.3")));
  }

  @Test
  void constraintsFilterVersions() {
    when(policies.listByScope("global", null)).thenReturn(List.of(doc("allow", "1.*")));
    assertEquals(Decision.ALLOW, decider.decide(request("1.2.3")));
    assertEquals(Decision.BLOCK, decider.decide(request("2.0.0")));
    assertTrue(ExtensionDecider.versionMatches(null, "9.9.9"));
    assertFalse(ExtensionDecider.versionMatches("1.2.3", "1.2.4"));
  }

  private static ExtensionPolicyDoc doc(String action, String constraint) {
    return new ExtensionPolicyDoc(
        UUID.randomUUID(), "global", null, "publisher.name", action, constraint, Instant.now());
  }

  private static ExtensionRequest request(String version) {
    return new ExtensionRequest(
        "publisher.name", version, UUID.randomUUID(), UUID.randomUUID());
  }
}
