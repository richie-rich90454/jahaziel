/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Coverage of {@link ExtensionPolicyEntity}. */
class ExtensionPolicyEntityTest {
  @Test
  void storesAllFields() {
    ExtensionPolicyEntity entity = new ExtensionPolicyEntity();
    UUID policyId = UUID.randomUUID();
    entity.setPolicyId(policyId);
    entity.setScope("workspace");
    entity.setScopeId("ws-1");
    entity.setExtensionId("publisher.ext");
    entity.setAction("allow");
    entity.setVersionConstraint(">=1.0");
    entity.setUpdatedAt(Instant.EPOCH);
    assertEquals(policyId, entity.getPolicyId());
    assertEquals("workspace", entity.getScope());
    assertEquals("ws-1", entity.getScopeId());
    assertEquals("publisher.ext", entity.getExtensionId());
    assertEquals("allow", entity.getAction());
    assertEquals(">=1.0", entity.getVersionConstraint());
    assertEquals(Instant.EPOCH, entity.getUpdatedAt());
  }

  @Test
  void equalsByPolicyId() {
    UUID policyId = UUID.randomUUID();
    ExtensionPolicyEntity first = new ExtensionPolicyEntity();
    first.setPolicyId(policyId);
    first.setAction("a");
    ExtensionPolicyEntity second = new ExtensionPolicyEntity();
    second.setPolicyId(policyId);
    second.setAction("b");
    assertTrue(first.equals(first));
    assertTrue(first.equals(second));
    assertEquals(first.hashCode(), second.hashCode());
    assertFalse(first.equals(null));
    assertFalse(first.equals((Object) "other"));
    second.setPolicyId(UUID.randomUUID());
    assertFalse(first.equals(second));
  }
}
