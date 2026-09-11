/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.authorization.api;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

/**
 * Workspace access decisions. Administrators may do anything; owners hold every permission on
 * their workspaces; everyone else needs an explicit grant.
 */
public interface AuthorizationService {
  /**
   * Checks a workspace permission.
   *
   * @param user the acting user
   * @param permission the permission name
   * @param workspace the targeted workspace
   * @return true when the action is allowed
   */
  boolean isAllowed(UserId user, String permission, WorkspaceId workspace);

  /**
   * Checks an administrator role.
   *
   * @param user the acting user
   * @return true when the user holds the administrator role
   */
  boolean isAdmin(UserId user);
}
