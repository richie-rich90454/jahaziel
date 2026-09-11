/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import dev.jahaziel.identity.api.UserNotFoundException;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.UserGroupRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import dev.jahaziel.spi.UserIdentity;
import java.util.List;
import java.util.Objects;

/** Database-backed users. */
public final class UserServiceImpl implements UserService {
  private final AppUserRepository users;
  private final UserRoleRepository roles;
  private final UserGroupRepository groups;

  /**
   * Creates the service.
   *
   * @param users the user rows
   * @param roles the role rows
   * @param groups the group rows
   */
  public UserServiceImpl(
      AppUserRepository users, UserRoleRepository roles, UserGroupRepository groups) {
    this.users = Objects.requireNonNull(users, "users");
    this.roles = Objects.requireNonNull(roles, "roles");
    this.groups = Objects.requireNonNull(groups, "groups");
  }

  @Override
  public UserIdentity get(UserId id) {
    AppUserEntity row = users.findById(id.value()).orElseThrow(() -> new UserNotFoundException(id));
    List<String> roleNames =
        roles.findByIdUserId(id.value()).stream().map(role -> role.getId().getRole()).toList();
    List<String> groupNames =
        groups.findByIdUserId(id.value()).stream()
            .map(group -> group.getId().getGroupName())
            .toList();
    return new UserIdentity(
        row.getExternalId(), row.getEmail(), row.getDisplayName(), roleNames, groupNames);
  }

  @Override
  public boolean exists(UserId id) {
    return users.existsById(id.value());
  }

  @Override
  public boolean isMfaEnabled(UserId id) {
    AppUserEntity row = users.findById(id.value()).orElseThrow(() -> new UserNotFoundException(id));
    return row.isMfaEnabled();
  }
}
