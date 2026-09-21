/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.authorization.internal;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.api.WorkspaceService;
import dev.jahaziel.workspace.domain.WorkspaceId;
import java.util.Objects;

/** Role, ownership, and grant checks. */
public final class AuthorizationServiceImpl implements AuthorizationService {
  private static final String ADMIN_ROLE = "ROLE_ADMIN";

  private final UserService users;
  private final WorkspaceService workspaces;

  /**
   * Creates the service.
   *
   * @param users the user lookup
   * @param workspaces the workspace lookup
   */
  public AuthorizationServiceImpl(UserService users, WorkspaceService workspaces) {
    this.users = Objects.requireNonNull(users, "users");
    this.workspaces = Objects.requireNonNull(workspaces, "workspaces");
  }

  @Override
  public boolean isAllowed(UserId user, String permission, WorkspaceId workspace) {
    Objects.requireNonNull(user, "user");
    if (permission == null || permission.isBlank()) {
      throw new IllegalArgumentException("permission must not be blank");
    }
    Objects.requireNonNull(workspace, "workspace");
    if (isAdmin(user)) {
      return true;
    }
    var found = workspaces.get(workspace);
    if (found.isOwnedBy(user)) {
      return true;
    }
    return found.acl().grants(user.value(), permission);
  }

  @Override
  public boolean isAdmin(UserId user) {
    return users.get(user).roles().contains(ADMIN_ROLE);
  }
}
