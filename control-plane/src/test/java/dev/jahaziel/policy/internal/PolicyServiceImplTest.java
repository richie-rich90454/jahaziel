/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.policy.api.PolicyDoc;
import dev.jahaziel.policy.api.PolicyNotFoundException;
import dev.jahaziel.policy.persistence.PolicyEntity;
import dev.jahaziel.policy.persistence.PolicyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Behavior of {@link PolicyServiceImpl} with stubbed rows. */
class PolicyServiceImplTest {
  private PolicyRepository policies;
  private PolicyServiceImpl service;
  private UUID policyId;

  @BeforeEach
  void setUp() {
    policies = mock(PolicyRepository.class);
    service = new PolicyServiceImpl(policies);
    policyId = UUID.randomUUID();
  }

  @Test
  void createPersistsWithFreshId() {
    PolicyDoc doc = service.create("workspace", null, "{\"allow\":true}", "admin");
    ArgumentCaptor<PolicyEntity> saved = ArgumentCaptor.forClass(PolicyEntity.class);
    verify(policies).save(saved.capture());
    assertNotNull(saved.getValue().getPolicyId());
    assertEquals(doc.id(), saved.getValue().getPolicyId());
    assertEquals("workspace", saved.getValue().getScope());
    assertEquals("{\"allow\":true}", saved.getValue().getPolicyJson());
    assertEquals("admin", saved.getValue().getUpdatedBy());
  }

  @Test
  void createRejectsBlanks() {
    assertThrows(IllegalArgumentException.class, () -> service.create(" ", null, "{}", "admin"));
    assertThrows(
        IllegalArgumentException.class, () -> service.create("workspace", null, " ", "admin"));
    assertThrows(
        IllegalArgumentException.class, () -> service.create("workspace", null, "{}", " "));
  }

  @Test
  void getReturnsMappedDoc() {
    PolicyEntity row = mock(PolicyEntity.class);
    when(row.getPolicyId()).thenReturn(policyId);
    when(row.getScope()).thenReturn("workspace");
    when(row.getScopeId()).thenReturn(null);
    when(row.getPolicyJson()).thenReturn("{}");
    when(row.getUpdatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    when(row.getUpdatedBy()).thenReturn("admin");
    when(policies.findById(policyId)).thenReturn(Optional.of(row));
    PolicyDoc doc = service.get(policyId);
    assertEquals(policyId, doc.id());
    assertEquals("workspace", doc.scope());
    assertEquals("admin", doc.updatedBy());
  }

  @Test
  void getMissingThrowsWithId() {
    when(policies.findById(policyId)).thenReturn(Optional.empty());
    PolicyNotFoundException failure =
        assertThrows(PolicyNotFoundException.class, () -> service.get(policyId));
    assertEquals(policyId, failure.policyId());
  }

  @Test
  void listFiltersByScope() {
    PolicyEntity global = mock(PolicyEntity.class);
    when(global.getPolicyId()).thenReturn(UUID.randomUUID());
    when(global.getScope()).thenReturn("workspace");
    when(global.getScopeId()).thenReturn(null);
    when(global.getPolicyJson()).thenReturn("{}");
    when(global.getUpdatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    when(global.getUpdatedBy()).thenReturn("admin");
    PolicyEntity scoped = mock(PolicyEntity.class);
    when(scoped.getPolicyId()).thenReturn(UUID.randomUUID());
    when(scoped.getScope()).thenReturn("workspace");
    when(scoped.getScopeId()).thenReturn("w-1");
    when(scoped.getPolicyJson()).thenReturn("{}");
    when(scoped.getUpdatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    when(scoped.getUpdatedBy()).thenReturn("admin");
    PolicyEntity other = mock(PolicyEntity.class);
    when(other.getScope()).thenReturn("node");
    when(other.getScopeId()).thenReturn(null);
    when(policies.findAll()).thenReturn(List.of(global, scoped, other));
    List<PolicyDoc> docs = service.listByScope("workspace", null);
    assertEquals(1, docs.size());
    assertEquals(global.getPolicyId(), docs.get(0).id());
    assertTrue(docs.stream().allMatch(doc -> "workspace".equals(doc.scope())));
  }

  @Test
  void deleteRemovesLoadedRow() {
    PolicyEntity row = mock(PolicyEntity.class);
    when(policies.findById(policyId)).thenReturn(Optional.of(row));
    service.delete(policyId);
    verify(policies).delete(row);
  }

  @Test
  void deleteMissingThrowsAndNeverDeletes() {
    when(policies.findById(policyId)).thenReturn(Optional.empty());
    PolicyNotFoundException failure =
        assertThrows(PolicyNotFoundException.class, () -> service.delete(policyId));
    assertEquals(policyId, failure.policyId());
    verify(policies, never()).delete(any());
  }
}
