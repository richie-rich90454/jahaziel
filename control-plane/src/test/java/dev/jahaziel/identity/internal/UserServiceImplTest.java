/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jahaziel.identity.api.AppUser;
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
    when(row.getUserId()).thenReturn(userId.value());
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

  @Test
  void mfaFlagComesFromRow() {
    AppUserEntity row = mock(AppUserEntity.class);
    when(row.isMfaEnabled()).thenReturn(true);
    when(users.findById(userId.value())).thenReturn(Optional.of(row));
    assertTrue(service.isMfaEnabled(userId));
    when(users.findById(userId.value())).thenReturn(Optional.empty());
    assertThrows(UserNotFoundException.class, () -> service.isMfaEnabled(userId));
  }

  @Test
  void updateStatusPersists() {
    AppUserEntity row = mock(AppUserEntity.class);
    when(users.findById(userId.value())).thenReturn(Optional.of(row));
    service.updateStatus(userId, "suspended");
    verify(row).setStatus("suspended");
    verify(users).save(row);
  }

  @Test
  void updateStatusRejectsBadInput() {
    assertThrows(IllegalArgumentException.class, () -> service.updateStatus(userId, "gone"));
    assertThrows(IllegalArgumentException.class, () -> service.updateStatus(userId, null));
    when(users.findById(userId.value())).thenReturn(Optional.empty());
    assertThrows(UserNotFoundException.class, () -> service.updateStatus(userId, "suspended"));
    verify(users, never()).save(any());
  }

  @Test
  void listUsersMapsRows() {
    AppUserEntity row = mock(AppUserEntity.class);
    when(row.getUserId()).thenReturn(userId.value());
    when(row.getExternalId()).thenReturn("sub-1");
    when(row.getEmail()).thenReturn("a@example.com");
    when(row.getDisplayName()).thenReturn("A");
    when(row.isMfaEnabled()).thenReturn(true);
    when(users.findAll()).thenReturn(List.of(row));
    when(roles.findByIdUserId(userId.value())).thenReturn(List.of());
    when(groups.findByIdUserId(userId.value())).thenReturn(List.of());
    List<AppUser> all = service.listUsers();
    assertEquals(1, all.size());
    assertEquals("sub-1", all.get(0).externalId());
    assertEquals(userId, all.get(0).id());
    assertTrue(all.get(0).mfaEnabled());
  }

  @Test
  void slugIsLowercaseEmailLocalPart() {
    AppUserEntity row = mock(AppUserEntity.class);
    when(row.getEmail()).thenReturn("Richard@Example.COM");
    when(users.findById(userId.value())).thenReturn(Optional.of(row));
    String slug = service.slug(userId);
    assertTrue(slug.startsWith("richard-"));
  }

  @Test
  void slugDiffersAcrossDomains() {
    AppUserEntity row = mock(AppUserEntity.class);
    when(users.findById(userId.value())).thenReturn(Optional.of(row));
    when(row.getEmail()).thenReturn("user@a.com");
    String first = service.slug(userId);
    when(row.getEmail()).thenReturn("user@b.com");
    String second = service.slug(userId);
    assertTrue(!first.equals(second));
  }

  @Test
  void slugRejectsMalformedEmail() {
    AppUserEntity row = mock(AppUserEntity.class);
    when(row.getEmail()).thenReturn("no-at-sign");
    when(users.findById(userId.value())).thenReturn(Optional.of(row));
    assertThrows(IllegalArgumentException.class, () -> service.slug(userId));
  }
}
