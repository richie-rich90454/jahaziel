/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.extension.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.extension.api.ExtensionPolicyDoc;
import dev.jahaziel.extension.api.ExtensionPolicyNotFoundException;
import dev.jahaziel.extension.persistence.ExtensionPolicyEntity;
import dev.jahaziel.extension.persistence.ExtensionPolicyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Behavior of {@link ExtensionPolicyServiceImpl} with stubbed rows. */
class ExtensionPolicyServiceImplTest {
  private ExtensionPolicyRepository policies;
  private ExtensionPolicyServiceImpl service;
  private UUID policyId;

  @BeforeEach
  void setUp() {
    policies = mock(ExtensionPolicyRepository.class);
    service = new ExtensionPolicyServiceImpl(policies);
    policyId = UUID.randomUUID();
  }

  @Test
  void createPersistsWithFreshId() {
    ExtensionPolicyDoc doc = service.create("workspace", null, "ext-1", "allow", null);
    ArgumentCaptor<ExtensionPolicyEntity> saved =
        ArgumentCaptor.forClass(ExtensionPolicyEntity.class);
    verify(policies).save(saved.capture());
    assertNotNull(saved.getValue().getPolicyId());
    assertEquals(doc.id(), saved.getValue().getPolicyId());
    assertEquals("workspace", saved.getValue().getScope());
    assertEquals("ext-1", saved.getValue().getExtensionId());
    assertEquals("allow", saved.getValue().getAction());
  }

  @Test
  void createRejectsBlanks() {
    assertThrows(
        IllegalArgumentException.class, () -> service.create(" ", null, "ext-1", "allow", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create("workspace", null, " ", "allow", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create("workspace", null, "ext-1", " ", null));
  }

  @Test
  void getReturnsMappedDoc() {
    ExtensionPolicyEntity row = mock(ExtensionPolicyEntity.class);
    when(row.getPolicyId()).thenReturn(policyId);
    when(row.getScope()).thenReturn("workspace");
    when(row.getScopeId()).thenReturn(null);
    when(row.getExtensionId()).thenReturn("ext-1");
    when(row.getAction()).thenReturn("allow");
    when(row.getVersionConstraint()).thenReturn(null);
    when(row.getUpdatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    when(policies.findById(policyId)).thenReturn(Optional.of(row));
    ExtensionPolicyDoc doc = service.get(policyId);
    assertEquals(policyId, doc.id());
    assertEquals("ext-1", doc.extensionId());
    assertEquals("allow", doc.action());
  }

  @Test
  void getMissingThrowsWithId() {
    when(policies.findById(policyId)).thenReturn(Optional.empty());
    ExtensionPolicyNotFoundException failure =
        assertThrows(ExtensionPolicyNotFoundException.class, () -> service.get(policyId));
    assertEquals(policyId, failure.policyId());
  }

  @Test
  void listFiltersByScope() {
    ExtensionPolicyEntity global = mock(ExtensionPolicyEntity.class);
    when(global.getPolicyId()).thenReturn(UUID.randomUUID());
    when(global.getScope()).thenReturn("workspace");
    when(global.getScopeId()).thenReturn(null);
    when(global.getExtensionId()).thenReturn("ext-1");
    when(global.getAction()).thenReturn("allow");
    when(global.getVersionConstraint()).thenReturn(null);
    when(global.getUpdatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    ExtensionPolicyEntity scoped = mock(ExtensionPolicyEntity.class);
    when(scoped.getPolicyId()).thenReturn(UUID.randomUUID());
    when(scoped.getScope()).thenReturn("workspace");
    when(scoped.getScopeId()).thenReturn("w-1");
    when(scoped.getExtensionId()).thenReturn("ext-1");
    when(scoped.getAction()).thenReturn("allow");
    when(scoped.getVersionConstraint()).thenReturn(">=2");
    when(scoped.getUpdatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    ExtensionPolicyEntity other = mock(ExtensionPolicyEntity.class);
    when(other.getScope()).thenReturn("node");
    when(other.getScopeId()).thenReturn(null);
    when(policies.findAll()).thenReturn(List.of(global, scoped, other));
    List<ExtensionPolicyDoc> docs = service.listByScope("workspace", null);
    assertEquals(1, docs.size());
    assertEquals(global.getPolicyId(), docs.get(0).id());
    assertTrue(docs.stream().allMatch(doc -> "workspace".equals(doc.scope())));
  }

  @Test
  void deleteRemovesLoadedRow() {
    ExtensionPolicyEntity row = mock(ExtensionPolicyEntity.class);
    when(policies.findById(policyId)).thenReturn(Optional.of(row));
    service.delete(policyId);
    verify(policies).delete(row);
  }

  @Test
  void deleteMissingThrowsAndNeverDeletes() {
    when(policies.findById(policyId)).thenReturn(Optional.empty());
    ExtensionPolicyNotFoundException failure =
        assertThrows(ExtensionPolicyNotFoundException.class, () -> service.delete(policyId));
    assertEquals(policyId, failure.policyId());
    verify(policies, never()).delete(any());
  }
}
