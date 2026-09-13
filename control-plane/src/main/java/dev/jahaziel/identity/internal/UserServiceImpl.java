/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import dev.jahaziel.identity.api.AppUser;
import dev.jahaziel.identity.api.UserNotFoundException;
import dev.jahaziel.identity.api.UserService;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.UserGroupRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import dev.jahaziel.spi.UserIdentity;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

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
    AppUser user = toAppUser(load(id));
    return new UserIdentity(
        user.externalId(), user.email(), user.displayName(), user.roles(), user.groups());
  }

  @Override
  public boolean exists(UserId id) {
    return users.existsById(id.value());
  }

  @Override
  public boolean isMfaEnabled(UserId id) {
    return load(id).isMfaEnabled();
  }

  @Override
  public void updateStatus(UserId id, String status) {
    if (status == null
        || (!status.equals("active") && !status.equals("suspended") && !status.equals("revoked"))) {
      throw new IllegalArgumentException("status must be active, suspended, or revoked");
    }
    AppUserEntity row = load(id);
    row.setStatus(status);
    users.save(row);
  }

  @Override
  public List<AppUser> listUsers() {
    return users.findAll().stream().map(this::toAppUser).toList();
  }

  @Override
  public String slug(UserId id) {
    String email = load(id).getEmail();
    int at = email.indexOf('@');
    if (at <= 0) {
      throw new IllegalArgumentException("user email has no local part");
    }
    String local = email.substring(0, at).toLowerCase(Locale.ROOT);
    String domain = email.substring(at + 1).toLowerCase(Locale.ROOT);
    return local + "-" + Integer.toUnsignedString(domain.hashCode(), 36);
  }

  private AppUserEntity load(UserId id) {
    return users.findById(id.value()).orElseThrow(() -> new UserNotFoundException(id));
  }

  private AppUser toAppUser(AppUserEntity row) {
    UUID id = row.getUserId();
    List<String> roleNames =
        roles.findByIdUserId(id).stream().map(role -> role.getId().getRole()).toList();
    List<String> groupNames =
        groups.findByIdUserId(id).stream().map(group -> group.getId().getGroupName()).toList();
    return new AppUser(
        new UserId(id),
        row.getExternalId(),
        row.getEmail(),
        row.getDisplayName(),
        roleNames,
        groupNames,
        row.isMfaEnabled());
  }
}
