/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.administration.api;

import dev.jahaziel.identity.api.AppUser;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.api.WorkspaceView;
import java.util.List;

/** Administrative actions. Every call writes an admin action row. */
public interface AdminService {
  /**
   * Suspends a user.
   *
   * @param userId the user to suspend
   * @param reason why the user is suspended
   * @param adminId the acting administrator
   */
  void suspendUser(UserId userId, String reason, UserId adminId);

  /**
   * Unsuspends a user.
   *
   * @param userId the user to unsuspend
   * @param reason why the user is unsuspended
   * @param adminId the acting administrator
   */
  void unsuspendUser(UserId userId, String reason, UserId adminId);

  /**
   * Lists all users.
   *
   * @return every user
   */
  List<AppUser> listUsers();

  /**
   * Lists all workspaces.
   *
   * @return every workspace view
   */
  List<WorkspaceView> listWorkspaces();
}
