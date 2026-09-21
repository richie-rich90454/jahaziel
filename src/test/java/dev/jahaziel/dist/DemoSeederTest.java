/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.dist;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.UserRoleEntity;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import dev.jahaziel.workspace.domain.Workspace;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link DemoSeeder} with stubbed stores. */
class DemoSeederTest {
  private AppUserRepository users;
  private UserRoleRepository roles;
  private WorkspaceService workspaces;
  private DemoSeeder seeder;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    roles = mock(UserRoleRepository.class);
    workspaces = mock(WorkspaceService.class);
    seeder = new DemoSeeder(users, roles, workspaces);
  }

  @Test
  void seedsUserAndWorkspaceOnFirstBoot() {
    when(users.existsById(DemoSeeder.DEMO_USER.value())).thenReturn(false);
    when(workspaces.describeByOwner(DemoSeeder.DEMO_USER)).thenReturn(List.of());
    seeder.run(null);
    verify(users).save(any(AppUserEntity.class));
    verify(roles).save(any(UserRoleEntity.class));
    verify(workspaces)
        .create(
            DemoSeeder.DEMO_USER,
            DemoSeeder.DEMO_WORKSPACE,
            2,
            4096,
            new dev.jahaziel.node.domain.NodeId("demo-node"));
  }

  @Test
  void secondBootChangesNothing() {
    when(users.existsById(DemoSeeder.DEMO_USER.value())).thenReturn(true);
    WorkspaceView view = mock(WorkspaceView.class);
    Workspace workspace = mock(Workspace.class);
    when(view.workspace()).thenReturn(workspace);
    when(workspace.name()).thenReturn(DemoSeeder.DEMO_WORKSPACE);
    when(workspaces.describeByOwner(DemoSeeder.DEMO_USER)).thenReturn(List.of(view));
    seeder.run(null);
    verify(users, never()).save(any());
    verify(roles, never()).save(any());
    verify(workspaces, never()).create(any(), any(), any(int.class), any(long.class), any());
  }

  @Test
  void existingUserWithoutWorkspaceCreatesOnlyWorkspace() {
    when(users.existsById(DemoSeeder.DEMO_USER.value())).thenReturn(true);
    when(workspaces.describeByOwner(DemoSeeder.DEMO_USER)).thenReturn(List.of());
    seeder.run(null);
    verify(users, never()).save(any());
    verify(workspaces)
        .create(
            DemoSeeder.DEMO_USER,
            DemoSeeder.DEMO_WORKSPACE,
            2,
            4096,
            new dev.jahaziel.node.domain.NodeId("demo-node"));
  }
}
