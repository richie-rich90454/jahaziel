/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.extension.api.ExtensionPolicyDoc;
import dev.jahaziel.extension.internal.ExtensionDecider;
import dev.jahaziel.extension.internal.ExtensionPolicyServiceImpl;
import dev.jahaziel.extension.persistence.ExtensionPolicyEntity;
import dev.jahaziel.extension.persistence.ExtensionPolicyRepository;
import dev.jahaziel.spi.Decision;
import dev.jahaziel.spi.ExtensionRequest;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExtensionExtraCoverageTest {
  private ExtensionRequest req(String ext, String ver) {
    return new ExtensionRequest(ext, ver, UUID.randomUUID(), UUID.randomUUID());
  }

  private ExtensionPolicyDoc doc(String scope, String action, String constraint) {
    return new ExtensionPolicyDoc(
        UUID.randomUUID(),
        scope,
        scope.equals("global") ? null : "s1",
        "ext.a",
        action,
        constraint,
        Instant.now());
  }

  @Test
  void deciderScopesAndActions() {
    var policies = mock(dev.jahaziel.extension.api.ExtensionPolicyService.class);
    ExtensionDecider decider = new ExtensionDecider(policies);
    ExtensionRequest r = req("ext.a", "1.2.3");
    when(policies.listByScope(any(), any())).thenReturn(List.of());
    assertEquals(Decision.BLOCK, decider.decide(r));
    when(policies.listByScope("workspace", r.workspaceId().toString()))
        .thenReturn(List.of(doc("workspace", "allow", null)));
    assertEquals(Decision.ALLOW, decider.decide(r));
    when(policies.listByScope("workspace", r.workspaceId().toString()))
        .thenReturn(List.of(doc("workspace", "require_approval", null)));
    assertEquals(Decision.REQUIRE_APPROVAL, decider.decide(r));
    when(policies.listByScope("workspace", r.workspaceId().toString()))
        .thenReturn(List.of(doc("workspace", "allow", null), doc("workspace", "block", null)));
    assertEquals(Decision.BLOCK, decider.decide(r));
    when(policies.listByScope("workspace", r.workspaceId().toString()))
        .thenReturn(List.of(doc("workspace", "weird", null)));
    assertEquals(Decision.BLOCK, decider.decide(r));
    when(policies.listByScope("workspace", r.workspaceId().toString())).thenReturn(List.of());
    when(policies.listByScope("user", r.userId().toString()))
        .thenReturn(List.of(doc("user", "allow", "1.2.*")));
    assertEquals(Decision.ALLOW, decider.decide(r));
    when(policies.listByScope("user", r.userId().toString())).thenReturn(List.of());
    when(policies.listByScope("global", null)).thenReturn(List.of(doc("global", "allow", null)));
    assertEquals(Decision.ALLOW, decider.decide(r));
    assertThrows(NullPointerException.class, () -> decider.decide(null));
    assertThrows(NullPointerException.class, () -> new ExtensionDecider(null));
  }

  @Test
  void versionMatchesBranches() throws Exception {
    Method m =
        ExtensionDecider.class.getDeclaredMethod("versionMatches", String.class, String.class);
    m.setAccessible(true);
    assertEquals(true, m.invoke(null, null, "1"));
    assertEquals(true, m.invoke(null, "", "1"));
    assertEquals(true, m.invoke(null, "  ", "1"));
    assertEquals(false, m.invoke(null, "1.*", null));
    assertEquals(true, m.invoke(null, "1.*", "1.2"));
    assertEquals(false, m.invoke(null, "1.*", "2.0"));
    assertEquals(true, m.invoke(null, "1.2.3", "1.2.3"));
    assertEquals(false, m.invoke(null, "1.2.3", "1.2.4"));
  }

  @Test
  void serviceValidationsAndScopeMatch() {
    ExtensionPolicyRepository repo = mock(ExtensionPolicyRepository.class);
    ExtensionPolicyServiceImpl svc = new ExtensionPolicyServiceImpl(repo);
    assertThrows(
        IllegalArgumentException.class, () -> svc.create("nope", null, "e", "allow", null));
    assertThrows(
        IllegalArgumentException.class, () -> svc.create("global", "x", "e", "allow", null));
    assertThrows(
        IllegalArgumentException.class, () -> svc.create("global", null, "", "allow", null));
    assertThrows(
        IllegalArgumentException.class, () -> svc.create("global", null, null, "allow", null));
    assertThrows(
        IllegalArgumentException.class, () -> svc.create("global", null, "e", "nope", null));
    assertThrows(IllegalArgumentException.class, () -> svc.listByScope(null, null));
    assertThrows(IllegalArgumentException.class, () -> svc.listByScope(" ", null));
    assertThrows(NullPointerException.class, () -> svc.get(null));
    assertThrows(NullPointerException.class, () -> svc.delete(null));
    when(repo.findById(any())).thenReturn(Optional.empty());
    assertThrows(
        dev.jahaziel.extension.api.ExtensionPolicyNotFoundException.class,
        () -> svc.get(UUID.randomUUID()));
    assertThrows(
        dev.jahaziel.extension.api.ExtensionPolicyNotFoundException.class,
        () -> svc.delete(UUID.randomUUID()));
    ExtensionPolicyEntity row = new ExtensionPolicyEntity();
    row.setPolicyId(UUID.randomUUID());
    row.setScope("global");
    row.setScopeId(null);
    row.setExtensionId("e");
    row.setAction("allow");
    row.setUpdatedAt(Instant.now());
    when(repo.findAll()).thenReturn(List.of(row));
    assertEquals(1, svc.listByScope("global", null).size());
    assertEquals(0, svc.listByScope("user", "s1").size());
    row.setScopeId("s1");
    row.setScope("user");
    when(repo.findAll()).thenReturn(List.of(row));
    assertEquals(0, svc.listByScope("user", null).size());
    assertEquals(1, svc.listByScope("user", "s1").size());
    assertThrows(NullPointerException.class, () -> new ExtensionPolicyServiceImpl(null));
  }

  @Test
  void policyDocGuards() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExtensionPolicyDoc(UUID.randomUUID(), "", null, "e", "allow", null, Instant.now()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExtensionPolicyDoc(UUID.randomUUID(), "s", null, "", "allow", null, Instant.now()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExtensionPolicyDoc(UUID.randomUUID(), "s", null, "e", "", null, Instant.now()));
  }
}
