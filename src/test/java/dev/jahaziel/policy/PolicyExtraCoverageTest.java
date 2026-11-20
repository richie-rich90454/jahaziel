/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.policy.api.PolicyDoc;
import dev.jahaziel.policy.internal.PolicyEvaluator;
import dev.jahaziel.policy.internal.PolicyServiceImpl;
import dev.jahaziel.policy.persistence.PolicyEntity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class PolicyExtraCoverageTest {
    @Test
    void evaluatorMergesAndHandlesEmpty() {
        dev.jahaziel.policy.api.PolicyService svc =
                mock(dev.jahaziel.policy.api.PolicyService.class);
        when(svc.listByScope(any(), any())).thenReturn(List.of());
        assertEquals(
                "{}", new PolicyEvaluator(svc).effectiveJson(Optional.empty(), Optional.empty()));
        PolicyDoc g =
                new PolicyDoc(
                        UUID.randomUUID(),
                        "global",
                        null,
                        "{\"a\":1,\"n\":{\"x\":1}}",
                        Instant.now(),
                        "op");
        PolicyDoc u =
                new PolicyDoc(
                        UUID.randomUUID(),
                        "user",
                        "u1",
                        "{\"a\":2,\"n\":{\"y\":2}}",
                        Instant.now(),
                        "op");
        PolicyDoc w =
                new PolicyDoc(
                        UUID.randomUUID(),
                        "workspace",
                        "w1",
                        "{\"n\":{\"x\":9}}",
                        Instant.now(),
                        "op");
        when(svc.listByScope("global", null)).thenReturn(List.of(g));
        when(svc.listByScope("user", "u1")).thenReturn(List.of(u));
        when(svc.listByScope("workspace", "w1")).thenReturn(List.of(w));
        String merged =
                new PolicyEvaluator(svc).effectiveJson(Optional.of("u1"), Optional.of("w1"));
        assertTrue(merged.contains("\"a\":2"));
        assertTrue(merged.contains("\"x\":9"));
        assertTrue(merged.contains("\"y\":2"));
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PolicyEvaluator(null);
                    }
                });
    }

    @Test
    void evaluatorRejectsBadJsonAndNonObject() {
        dev.jahaziel.policy.api.PolicyService svc =
                mock(dev.jahaziel.policy.api.PolicyService.class);
        PolicyDoc bad =
                new PolicyDoc(UUID.randomUUID(), "global", null, "not-json", Instant.now(), "op");
        when(svc.listByScope("global", null)).thenReturn(List.of(bad));
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PolicyEvaluator(svc).effectiveJson(Optional.empty(), Optional.empty());
                    }
                });
        PolicyDoc arr =
                new PolicyDoc(UUID.randomUUID(), "global", null, "[1,2]", Instant.now(), "op");
        when(svc.listByScope("global", null)).thenReturn(List.of(arr));
        assertThrows(
                Exception.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PolicyEvaluator(svc).effectiveJson(Optional.empty(), Optional.empty());
                    }
                });
    }

    @Test
    void serviceValidations() {
        dev.jahaziel.policy.persistence.PolicyRepository repo =
                mock(dev.jahaziel.policy.persistence.PolicyRepository.class);
        PolicyServiceImpl svc = new PolicyServiceImpl(repo);
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.create("nope", null, "{}", "op");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.create("global", "x", "{}", "op");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.create("global", null, "", "op");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.create("global", null, null, "op");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.create("global", null, "{}", "");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.create("global", null, "{}", null);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.listByScope(null, null);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.listByScope(" ", null);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.get(null);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.delete(null);
                    }
                });
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThrows(
                dev.jahaziel.policy.api.PolicyNotFoundException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.get(UUID.randomUUID());
                    }
                });
        assertThrows(
                dev.jahaziel.policy.api.PolicyNotFoundException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        svc.delete(UUID.randomUUID());
                    }
                });
        PolicyEntity row = new PolicyEntity();
        row.setPolicyId(UUID.randomUUID());
        row.setScope("global");
        row.setScopeId(null);
        row.setPolicyJson("{}");
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy("op");
        when(repo.findAll()).thenReturn(List.of(row));
        assertEquals(1, svc.listByScope("global", null).size());
        assertEquals(0, svc.listByScope("user", "u").size());
        row.setScope("user");
        row.setScopeId("u");
        assertEquals(1, svc.listByScope("user", "u").size());
        assertEquals(0, svc.listByScope("user", null).size());
        assertThrows(
                NullPointerException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PolicyServiceImpl(null);
                    }
                });
    }

    @Test
    void policyDocGuards() {
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PolicyDoc(UUID.randomUUID(), "", null, "{}", Instant.now(), "op");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PolicyDoc(UUID.randomUUID(), "s", null, "", Instant.now(), "op");
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    public void execute() throws Throwable {
                        new PolicyDoc(UUID.randomUUID(), "s", null, "{}", Instant.now(), "");
                    }
                });
    }
}
