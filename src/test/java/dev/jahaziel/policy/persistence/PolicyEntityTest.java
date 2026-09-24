/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coverage of {@link PolicyEntity}. */
class PolicyEntityTest {
  @Test
  void storesAllFields() {
    PolicyEntity entity = new PolicyEntity();
    UUID policyId = UUID.randomUUID();
    entity.setPolicyId(policyId);
    entity.setScope("workspace");
    entity.setScopeId(UUID.randomUUID().toString());
    entity.setPolicyJson("{}");
    entity.setUpdatedAt(Instant.EPOCH);
    entity.setUpdatedBy("admin");
    assertEquals(policyId, entity.getPolicyId());
    assertEquals("workspace", entity.getScope());
    assertEquals("{}", entity.getPolicyJson());
    assertEquals(Instant.EPOCH, entity.getUpdatedAt());
    assertEquals("admin", entity.getUpdatedBy());
  }

  @Test
  void equalsByPolicyId() {
    UUID policyId = UUID.randomUUID();
    PolicyEntity first = new PolicyEntity();
    first.setPolicyId(policyId);
    first.setScope("a");
    PolicyEntity second = new PolicyEntity();
    second.setPolicyId(policyId);
    second.setScope("b");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setPolicyId(UUID.randomUUID());
    assertFalse(first.equals(second));
  }
}
