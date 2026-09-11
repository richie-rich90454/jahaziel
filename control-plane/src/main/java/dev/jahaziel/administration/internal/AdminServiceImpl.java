/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.administration.internal;

import dev.jahaziel.administration.api.AdminService;
import dev.jahaziel.administration.persistence.AdminActionEntity;
import dev.jahaziel.administration.persistence.AdminActionRepository;
import dev.jahaziel.identity.api.AppUser;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.api.WorkspaceView;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Database-backed administration. */
public final class AdminServiceImpl implements AdminService {
  private final UserService users;
  private final WorkspaceService workspaces;
  private final AdminActionRepository actions;

  /**
   * Creates the service.
   *
   * @param users the user service
   * @param workspaces the workspace service
   * @param actions the admin action rows
   */
  public AdminServiceImpl(UserService users, WorkspaceService workspaces, AdminActionRepository actions) {
    this.users = Objects.requireNonNull(users, "users");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
    this.actions = Objects.requireNonNull(actions, "actions");
  }

  @Override
  public void suspendUser(UserId userId, String reason, UserId adminId) {
    checkReason(reason);
    Objects.requireNonNull(adminId, "adminId");
    users.updateStatus(userId, "suspended");
    record(adminId, "user.suspend", userId.value().toString(), reason);
  }

  @Override
  public void unsuspendUser(UserId userId, String reason, UserId adminId) {
    checkReason(reason);
    Objects.requireNonNull(adminId, "adminId");
    users.updateStatus(userId, "active");
    record(adminId, "user.unsuspend", userId.value().toString(), reason);
  }

  @Override
  public List<AppUser> listUsers() {
    return users.listUsers();
  }

  @Override
  public List<WorkspaceView> listWorkspaces() {
    return workspaces.describeAll();
  }

  private void record(UserId adminId, String action, String target, String reason) {
    AdminActionEntity row = new AdminActionEntity();
    row.setActionId(UUID.randomUUID());
    row.setTimestamp(Instant.now());
    row.setAdminUserId(adminId.value());
    row.setAction(action);
    row.setTarget(target);
    row.setDetails("{}");
    row.setReason(reason);
    row.setBreakGlass(false);
    actions.save(row);
  }

  private static void checkReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
  }
}
