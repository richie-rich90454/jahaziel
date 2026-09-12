/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.administration.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.administration.persistence.AdminActionRepository;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.api.WorkspaceService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Behavior of {@link AdminServiceImpl} with stubbed collaborators. */
class AdminServiceImplTest {
  private UserService users;
  private WorkspaceService workspaces;
  private AdminActionRepository actions;
  private AdminServiceImpl service;
  private UserId userId;
  private UserId adminId;

  @BeforeEach
  void setUp() {
    users = mock(UserService.class);
    workspaces = mock(WorkspaceService.class);
    actions = mock(AdminActionRepository.class);
    service = new AdminServiceImpl(users, workspaces, actions);
    userId = new UserId(UUID.randomUUID());
    adminId = new UserId(UUID.randomUUID());
  }

  @Test
  void suspendFlipsStatusAndRecords() {
    service.suspendUser(userId, "abuse", adminId);
    verify(users).updateStatus(userId, "suspended");
    assertEquals("user.suspend", savedAction());
  }

  @Test
  void unsuspendFlipsStatusAndRecords() {
    service.unsuspendUser(userId, "appeal granted", adminId);
    verify(users).updateStatus(userId, "active");
    assertEquals("user.unsuspend", savedAction());
  }

  @Test
  void blankReasonsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> service.suspendUser(userId, "  ", adminId));
    assertThrows(IllegalArgumentException.class, () -> service.unsuspendUser(userId, null, adminId));
  }

  @Test
  void listsDelegate() {
    when(users.listUsers()).thenReturn(List.of());
    when(workspaces.describeAll()).thenReturn(List.of());
    assertEquals(List.of(), service.listUsers());
    assertEquals(List.of(), service.listWorkspaces());
  }

  private String savedAction() {
    var captor =
        ArgumentCaptor.forClass(dev.jahaziel.administration.persistence.AdminActionEntity.class);
    verify(actions).save(captor.capture());
    return captor.getValue().getAction();
  }
}
