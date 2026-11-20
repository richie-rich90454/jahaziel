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
import dev.jahaziel.identity.persistence.UserGroupEntity;
import dev.jahaziel.identity.persistence.UserGroupRepository;
import dev.jahaziel.identity.persistence.UserRoleEntity;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import dev.jahaziel.spi.UserIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Database-backed users. */
public final class UserServiceImpl extends UserService {
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
    public boolean isActive(UserId id) {
        Optional<AppUserEntity> found = users.findById(id.value());
        if (!found.isPresent()) {
            return false;
        }
        return "active".equals(found.get().getStatus());
    }

    @Override
    public boolean isMfaEnabled(UserId id) {
        return load(id).isMfaEnabled();
    }

    @Override
    public void updateStatus(UserId id, String status) {
        if (status == null
                || (!status.equals("active")
                        && !status.equals("suspended")
                        && !status.equals("revoked"))) {
            throw new IllegalArgumentException("status must be active, suspended, or revoked");
        }
        AppUserEntity row = load(id);
        row.setStatus(status);
        users.save(row);
    }

    @Override
    public List<AppUser> listUsers() {
        List<AppUser> result = new ArrayList<AppUser>();
        List<AppUserEntity> rows = users.findAll();
        for (int index = 0; index < rows.size(); index++) {
            result.add(toAppUser(rows.get(index)));
        }
        return result;
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
        Optional<AppUserEntity> found = users.findById(id.value());
        if (!found.isPresent()) {
            throw new UserNotFoundException(id);
        }
        return found.get();
    }

    private AppUser toAppUser(AppUserEntity row) {
        UUID id = row.getUserId();
        List<String> roleNames = new ArrayList<String>();
        List<UserRoleEntity> roleRows = roles.findByIdUserId(id);
        for (int index = 0; index < roleRows.size(); index++) {
            roleNames.add(roleRows.get(index).getId().getRole());
        }
        List<String> groupNames = new ArrayList<String>();
        List<UserGroupEntity> groupRows = groups.findByIdUserId(id);
        for (int index = 0; index < groupRows.size(); index++) {
            groupNames.add(groupRows.get(index).getId().getGroupName());
        }
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
