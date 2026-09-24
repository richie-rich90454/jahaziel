/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.UserGroupEntity;
import dev.jahaziel.identity.persistence.UserGroupRepository;
import dev.jahaziel.identity.persistence.UserRoleEntity;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserServiceCoverageGapTest {
  @Test
  void toAppUserMapsRoleAndGroupNames() {
    var users = mock(AppUserRepository.class);
    var roles = mock(UserRoleRepository.class);
    var groups = mock(UserGroupRepository.class);
    UserServiceImpl svc = new UserServiceImpl(users, roles, groups);
    UUID id = UUID.randomUUID();
    AppUserEntity row = new AppUserEntity();
    row.setUserId(id);
    row.setExternalId("sub-1");
    row.setEmail("a@example.com");
    row.setDisplayName("A");
    row.setMfaEnabled(false);
    when(users.findById(id)).thenReturn(Optional.of(row));
    UserRoleEntity role = new UserRoleEntity();
    role.setId(new UserRoleEntity.Id(id, "ROLE_ADMIN"));
    when(roles.findByIdUserId(id)).thenReturn(List.of(role));
    UserGroupEntity group = new UserGroupEntity();
    group.setId(new UserGroupEntity.Id(id, "devs"));
    when(groups.findByIdUserId(id)).thenReturn(List.of(group));
    var identity = svc.get(new UserId(id));
    assertEquals(List.of("ROLE_ADMIN"), identity.roles());
    assertEquals(List.of("devs"), identity.groups());
  }

  @Test
  void updateStatusAcceptsActiveAndRevoked() {
    var users = mock(AppUserRepository.class);
    var roles = mock(UserRoleRepository.class);
    var groups = mock(UserGroupRepository.class);
    UserServiceImpl svc = new UserServiceImpl(users, roles, groups);
    UserId id = new UserId(UUID.randomUUID());
    AppUserEntity row = mock(AppUserEntity.class);
    when(users.findById(id.value())).thenReturn(Optional.of(row));
    svc.updateStatus(id, "active");
    verify(row).setStatus("active");
    svc.updateStatus(id, "revoked");
    verify(row).setStatus("revoked");
    assertTrue(svc.isActive(id) == false);
  }
}
