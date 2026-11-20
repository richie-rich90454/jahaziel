/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.administration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.administration.internal.AdminServiceImpl;
import dev.jahaziel.administration.persistence.AdminActionRepository;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.api.WorkspaceService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.UUID;

class AdministrationExtraCoverageTest {
    @Test
    void suspendUnsuspendAndLists() {
        UserService users = mock(UserService.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        AdminActionRepository actions = mock(AdminActionRepository.class);
        AdminServiceImpl svc = new AdminServiceImpl(users, workspaces, actions);
        UserId u = new UserId(UUID.randomUUID());
        UserId admin = new UserId(UUID.randomUUID());
        svc.suspendUser(u, "abuse", admin);
        verify(users).updateStatus(u, "suspended");
        verify(actions).save(any());
        svc.unsuspendUser(u, "appeal", admin);
        verify(users).updateStatus(u, "active");
        when(users.listUsers()).thenReturn(List.of());
        assertTrue(svc.listUsers().isEmpty());
        when(workspaces.describeAll()).thenReturn(List.of());
        assertTrue(svc.listWorkspaces().isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        svc.suspendUser(u, null, admin);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        svc.suspendUser(u, " ", admin);
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        svc.unsuspendUser(u, "", admin);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        svc.suspendUser(u, "r", null);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        svc.unsuspendUser(u, "r", null);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new AdminServiceImpl(null, workspaces, actions);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new AdminServiceImpl(users, null, actions);
                    }
                });
        assertThrows(
                NullPointerException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        new AdminServiceImpl(users, workspaces, null);
                    }
                });
    }
}
