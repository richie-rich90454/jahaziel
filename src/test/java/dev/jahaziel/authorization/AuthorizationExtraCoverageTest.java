/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.authorization.internal.AuthorizationServiceImpl;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.spi.UserIdentity;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.AccessControlList;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.UUID;

class AuthorizationExtraCoverageTest {
    @Test
    void adminShortCircuitsAndGuards() {
        UserService users = mock(UserService.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        AuthorizationServiceImpl auth = new AuthorizationServiceImpl(users, workspaces);
        UserId admin = new UserId(UUID.randomUUID());
        WorkspaceId w = new WorkspaceId(UUID.randomUUID());
        UserIdentity adminUser = new UserIdentity("s", "e", "N", List.of("ROLE_ADMIN"), List.of());
        when(users.get(admin)).thenReturn(adminUser);
        assertTrue(auth.isAllowed(admin, "write", w));
        assertTrue(auth.isAdmin(admin));
        UserId u = new UserId(UUID.randomUUID());
        UserIdentity plain = new UserIdentity("s", "e", "N", List.of("ROLE_USER"), List.of());
        when(users.get(u)).thenReturn(plain);
        assertFalse(auth.isAdmin(u));
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        auth.isAllowed(null, "r", w);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        auth.isAllowed(u, null, w);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        auth.isAllowed(u, " ", w);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        auth.isAllowed(u, "r", null);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new AuthorizationServiceImpl(null, workspaces);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new AuthorizationServiceImpl(users, null);
                    }
                });
    }

    @Test
    void ownerAndAclBranches() {
        UserService users = mock(UserService.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        AuthorizationServiceImpl auth = new AuthorizationServiceImpl(users, workspaces);
        UserId u = new UserId(UUID.randomUUID());
        WorkspaceId w = new WorkspaceId(UUID.randomUUID());
        UserIdentity plain = new UserIdentity("s", "e", "N", List.of(), List.of());
        when(users.get(u)).thenReturn(plain);
        Workspace owned = mock(Workspace.class);
        when(owned.isOwnedBy(u)).thenReturn(true);
        when(workspaces.get(w)).thenReturn(owned);
        assertTrue(auth.isAllowed(u, "read", w));
        Workspace foreign = mock(Workspace.class);
        when(foreign.isOwnedBy(u)).thenReturn(false);
        AccessControlList acl = mock(AccessControlList.class);
        when(foreign.acl()).thenReturn(acl);
        when(workspaces.get(w)).thenReturn(foreign);
        when(acl.grants(u.value(), "read")).thenReturn(true);
        assertTrue(auth.isAllowed(u, "read", w));
        when(acl.grants(u.value(), "read")).thenReturn(false);
        assertFalse(auth.isAllowed(u, "read", w));
    }
}
