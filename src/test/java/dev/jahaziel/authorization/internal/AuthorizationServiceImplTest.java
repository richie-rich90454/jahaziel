/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.authorization.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.VirtualMachine;
import dev.jahaziel.workspace.domain.VirtualMachineConfiguration;
import dev.jahaziel.workspace.domain.VirtualMachineId;
import dev.jahaziel.spi.UserIdentity;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.Workspace;
import dev.jahaziel.workspace.domain.WorkspaceId;
import dev.jahaziel.workspace.domain.WorkspaceQuota;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link AuthorizationServiceImpl} with stubbed lookups. */
class AuthorizationServiceImplTest {
  private UserService users;
  private WorkspaceService workspaces;
  private AuthorizationServiceImpl service;
  private UserId user;
  private WorkspaceId workspaceId;

  @BeforeEach
  void setUp() {
    users = mock(UserService.class);
    workspaces = mock(WorkspaceService.class);
    service = new AuthorizationServiceImpl(users, workspaces);
    user = new UserId(UUID.randomUUID());
    workspaceId = new WorkspaceId(UUID.randomUUID());
  }

  @Test
  void adminMayDoAnything() {
    when(users.get(user)).thenReturn(identity(List.of("ROLE_ADMIN")));
    assertTrue(service.isAllowed(user, "workspace.delete", workspaceId));
    assertTrue(service.isAdmin(user));
  }

  @Test
  void ownerHoldsEveryPermission() {
    when(users.get(user)).thenReturn(identity(List.of()));
    when(workspaces.get(workspaceId)).thenReturn(workspace(user));
    assertTrue(service.isAllowed(user, "workspace.delete", workspaceId));
  }

  @Test
  void grantDecidesForStrangers() {
    UserId stranger = new UserId(UUID.randomUUID());
    when(users.get(stranger)).thenReturn(identity(List.of()));
    Workspace owned = workspace(user);
    owned.grant(stranger.value(), "read");
    when(workspaces.get(workspaceId)).thenReturn(owned);
    assertTrue(service.isAllowed(stranger, "read", workspaceId));
    assertFalse(service.isAllowed(stranger, "write", workspaceId));
  }

  @Test
  void blankPermissionIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> service.isAllowed(user, "  ", workspaceId));
  }

  private UserIdentity identity(List<String> roles) {
    return new UserIdentity("sub", "a@b.c", "A", roles, List.of());
  }

  private Workspace workspace(UserId owner) {
    return new Workspace(
        workspaceId,
        owner,
        "club",
        new WorkspaceQuota(21474836480L, 1000000L),
        new VirtualMachine(
            new VirtualMachineId(UUID.randomUUID()),
            workspaceId,
            new VirtualMachineConfiguration(2, 2048)));
  }
}
