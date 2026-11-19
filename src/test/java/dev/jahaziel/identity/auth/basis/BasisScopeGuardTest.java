/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class BasisScopeGuardTest {
    private final BasisScopeGuard guard = new BasisScopeGuard();

    @Test
    void exactScopeAndPermissionAreAccepted() {
        Set<String> scopes = set("projects.read", "projects.write");
        Set<String> permissions = set("projects.member");

        guard.requireScopes(scopes, "projects.read");
        guard.requirePermissions(permissions, "projects.member");
    }

    @Test
    void namespacePermissionCoversDescendants() {
        Set<String> permissions = set("nethack.Projects.all");

        guard.requirePermissions(permissions, "nethack.Projects.read");
        guard.requirePermissions(permissions, "nethack.Projects.write.deep");
    }

    @Test
    void missingScopeHasDocumentedCode() {
        BasisScopeGuard.ScopeDeniedException denied =
                assertThrows(
                        BasisScopeGuard.ScopeDeniedException.class,
                        new ScopeCall(set("projects.read"), "projects.write"));
        assertEquals(403, denied.status());
        assertEquals("insufficient_scope", denied.code());
    }

    @Test
    void missingPermissionHasDocumentedCode() {
        BasisScopeGuard.ScopeDeniedException denied =
                assertThrows(
                        BasisScopeGuard.ScopeDeniedException.class,
                        new PermissionCall(set("projects.read"), "projects.admin"));
        assertEquals(403, denied.status());
        assertEquals("insufficient_permissions", denied.code());
    }

    @Test
    void unrelatedNamespaceDoesNotGrantPermission() {
        assertThrows(
                BasisScopeGuard.ScopeDeniedException.class,
                new PermissionCall(set("nethack.Other.all"), "nethack.Projects.read"));
    }

    private static Set<String> set(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    private final class ScopeCall implements Executable {
        private final Set<String> granted;
        private final String required;

        private ScopeCall(Set<String> granted, String required) {
            this.granted = granted;
            this.required = required;
        }

        @Override
        public void execute() {
            guard.requireScopes(granted, required);
        }
    }

    private final class PermissionCall implements Executable {
        private final Set<String> granted;
        private final String required;

        private PermissionCall(Set<String> granted, String required) {
            this.granted = granted;
            this.required = required;
        }

        @Override
        public void execute() {
            guard.requirePermissions(granted, required);
        }
    }
}
