/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.UserNotFoundException;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.identity.persistence.AppUserEntity;
import dev.jahaziel.identity.persistence.AppUserRepository;
import dev.jahaziel.identity.persistence.UserGroupRepository;
import dev.jahaziel.identity.persistence.UserRoleRepository;
import dev.jahaziel.spi.UserIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behavior of {@link UserServiceImpl} with stubbed rows. */
class UserServiceImplTest {
  private AppUserRepository users;
  private UserRoleRepository roles;
  private UserGroupRepository groups;
  private UserServiceImpl service;
  private UserId userId;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    roles = mock(UserRoleRepository.class);
    groups = mock(UserGroupRepository.class);
    service = new UserServiceImpl(users, roles, groups);
    userId = new UserId(UUID.randomUUID());
  }

  @Test
  void returnsIdentityWithRolesAndGroups() {
    AppUserEntity row = mock(AppUserEntity.class);
    when(row.getExternalId()).thenReturn("sub-1");
    when(row.getEmail()).thenReturn("a@example.com");
    when(row.getDisplayName()).thenReturn("A");
    when(users.findById(userId.value())).thenReturn(Optional.of(row));
    when(roles.findByIdUserId(userId.value())).thenReturn(List.of());
    when(groups.findByIdUserId(userId.value())).thenReturn(List.of());
    UserIdentity identity = service.get(userId);
    assertEquals("sub-1", identity.externalId());
    assertEquals(List.of(), identity.roles());
  }

  @Test
  void missingUserThrows() {
    when(users.findById(userId.value())).thenReturn(Optional.empty());
    UserNotFoundException failure =
        assertThrows(UserNotFoundException.class, () -> service.get(userId));
    assertEquals(userId, failure.userId());
  }

  @Test
  void existsDelegates() {
    when(users.existsById(userId.value())).thenReturn(true);
    assertTrue(service.exists(userId));
    assertFalse(service.exists(new UserId(UUID.randomUUID())));
  }
}
