/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jahaziel.extension.api.ExtensionPolicyDoc;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Validation of the policy document records. */
class PolicyRecordTest {
  @Test
  void policyDocAllowsGlobalScope() {
    PolicyDoc doc =
        new PolicyDoc(UUID.randomUUID(), "workspace", null, "{}", Instant.now(), "admin");
    assertEquals("workspace", doc.scope());
    assertNull(doc.scopeId());
    assertThrows(NullPointerException.class, () -> new PolicyDoc(null, "s", null, "{}", at(), "a"));
    assertThrows(NullPointerException.class, () -> new PolicyDoc(id(), "s", null, "{}", null, "a"));
    assertThrows(
        NullPointerException.class, () -> new PolicyDoc(id(), "s", null, "{}", at(), null));
    assertThrows(
        IllegalArgumentException.class, () -> new PolicyDoc(id(), null, null, "{}", at(), "a"));
    assertThrows(
        IllegalArgumentException.class, () -> new PolicyDoc(id(), "  ", null, "{}", at(), "a"));
    assertThrows(
        IllegalArgumentException.class, () -> new PolicyDoc(id(), "s", null, null, at(), "a"));
    assertThrows(
        IllegalArgumentException.class, () -> new PolicyDoc(id(), "s", null, "  ", at(), "a"));
    assertThrows(
        IllegalArgumentException.class, () -> new PolicyDoc(id(), "s", null, "{}", at(), "  "));
  }

  @Test
  void extensionPolicyDocAllowsNullsForGlobalScope() {
    ExtensionPolicyDoc doc =
        new ExtensionPolicyDoc(
            UUID.randomUUID(), "workspace", null, "publisher.ext", "allow", null, Instant.now());
    assertEquals("allow", doc.action());
    assertNull(doc.scopeId());
    assertNull(doc.versionConstraint());
    assertThrows(
        NullPointerException.class,
        () -> new ExtensionPolicyDoc(null, "s", null, "e", "a", null, at()));
    assertThrows(
        NullPointerException.class,
        () -> new ExtensionPolicyDoc(id(), "s", null, "e", "a", null, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExtensionPolicyDoc(id(), null, null, "e", "a", null, at()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExtensionPolicyDoc(id(), "  ", null, "e", "a", null, at()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExtensionPolicyDoc(id(), "s", null, null, "a", null, at()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExtensionPolicyDoc(id(), "s", null, "  ", "a", null, at()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExtensionPolicyDoc(id(), "s", null, "e", null, null, at()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExtensionPolicyDoc(id(), "s", null, "e", "  ", null, at()));
  }

  private static UUID id() {
    return UUID.randomUUID();
  }

  private static Instant at() {
    return Instant.now();
  }
}
